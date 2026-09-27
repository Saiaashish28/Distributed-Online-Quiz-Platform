# QuizSphere — Distributed Systems Design Notes

This document explains how QuizSphere behaves as a distributed system: many browsers talking concurrently to a stateless API backed by one PostgreSQL database, with a WebSocket channel for real-time events. It covers consistency, concurrency control, time, failure handling, and what changes when the backend runs as several instances.

## 1. Architecture

```text
 Browser (student) ──HTTPS REST──┐                     ┌──────────────────────┐
 Browser (student) ──WSS events──┤   Spring Boot API   │  PostgreSQL          │
 Browser (admin)   ──HTTPS/WSS───┤  (stateless JWT)    │  source of truth     │
                                 │  + WebSocket hub     ├─ attempts, answers,  │
                                 │  + scheduler         │  events, rosters…    │
                                 └─────────────────────┘└──────────────────────┘
```

- **REST is the system of record for every state change.** Starting an attempt, saving answers, submitting, starting/ending a session and releasing results are HTTP requests that commit to PostgreSQL.
- **WebSocket is a notification channel only.** Events (`session_started`, `submission_progress`, …) are sent *after* the database transaction commits. Losing an event never loses data: clients re-read state from REST, and every (re)connection receives an authoritative `session_snapshot` built from the database.
- **The API is stateless** apart from two in-memory, reconstructible components: the WebSocket connection registry and the login/join rate limiter (see §7).

## 2. Time: the server clock is authoritative

Clients are not trusted to keep time.

- When an attempt starts, the server fixes `ends_at = min(start + duration, deadline, live-session end)` and stores it.
- Every attempt/save response includes `serverTime`; the client computes a clock offset and renders the countdown as `endsAt − (now + offset)`. The countdown is display-only.
- Saves and submits are checked against `ends_at + grace` (`QUIZ_GRACE_SECONDS`, default 5 s) to tolerate network latency. Later saves are rejected with `409`; a late submit is recorded as `AUTO_SUBMITTED`.
- A scheduler (`QuizScheduler`, every 10 s) finalizes attempts whose `ends_at + grace` has passed and ends overdue live sessions, so deadlines are enforced even if the student's browser is closed or offline. Attempts are also finalized lazily when a student reopens them.

## 3. Concurrency control

| Operation | Hazard | Mechanism |
|---|---|---|
| Start attempt | Double-click / two tabs create two attempts, or exceed `max_attempts` | `SELECT … FOR UPDATE` on the student row serializes starts per student; partial unique index `ux_attempts_in_progress (assignment_id, student_id) WHERE status='IN_PROGRESS'` and unique `(assignment_id, student_id, attempt_number)` as a database backstop |
| Save answer | Two in-flight saves arrive out of order; a slow retry overwrites a newer choice | Upsert `INSERT … ON CONFLICT (attempt_id, question_id) DO UPDATE … WHERE answers.client_seq < EXCLUDED.client_seq`. The client sends a monotonic sequence number (`max(Date.now(), last+1)`), so an older write can never win |
| Save vs. submit race | An answer lands after scoring | Both take `SELECT … FOR UPDATE` on the attempt row; saves re-check status after acquiring the lock |
| Submit | Retries or double submits change the score or duplicate records | Submit locks the attempt; if already final it returns the stored result unchanged (idempotent). Verified by a test firing six concurrent submits |
| Start / end live session | Two admins (or tabs) start the same session | `SELECT … FOR UPDATE` on the assignment plus a state check (`WAITING → LIVE → ENDED` only) and an optimistic `@Version`; a concurrent-start test asserts exactly one success |
| Scheduler vs. user | Scheduler auto-submits while the student submits | Same attempt row lock; whichever runs second sees a final attempt and does nothing |

Locks are always taken by re-reading the row (`refresh(..., PESSIMISTIC_WRITE)`), so a decision is never made on state loaded before the lock was granted.

## 4. Consistency model

- All writes are single-database ACID transactions; there are no distributed transactions.
- Scoring happens in the same transaction that marks the attempt final, so a submitted attempt always has a score consistent with its stored answers.
- Published quizzes are immutable while they can be attempted (edits require moving back to draft, which is refused once any attempt exists). Question-bank questions are **copied** into quizzes. Therefore an attempt's questions and answer key cannot change underneath it.
- Group membership for DYNAMIC groups is computed from current data at read time, so eligibility is always consistent with the roster; MANUAL groups are explicit rows.
- WebSocket delivery is at-most-once and best-effort. Correctness never depends on it (see §6).

## 5. Idempotency and retries

- `PUT /attempts/{id}/answers` is idempotent per `(question, seq)` and safe to retry.
- `POST /attempts/{id}/submit` is idempotent.
- `POST /session/end` on an ended session is a no-op.
- The quiz page keeps unsaved selections in `localStorage` and in memory, retries with exponential backoff (1 s → 15 s), resumes immediately on the browser `online` event, and re-applies pending answers after a refresh or crash (only if their sequence number is newer than the server's).

## 6. Failure scenarios

| Failure | Behaviour |
|---|---|
| Student loses network mid-quiz | Answers queue locally; the header shows "Not saved — retrying"; saves flush on reconnect. If the deadline passes meanwhile, the server auto-submits what it already has |
| Student refreshes or reopens the browser | `GET /attempts/{id}` returns saved answers and the fixed `endsAt`; the timer continues rather than resetting (verified in the end-to-end run) |
| WebSocket drops | Client reconnects with backoff (1 s → 30 s) and receives a fresh `session_snapshot`; the quiz page also re-checks the attempt status |
| Missed `session_started` / `results_released` event | Waiting room and dashboard reload state from REST on snapshot or navigation |
| Backend restarts | No quiz state is lost (all in PostgreSQL). Clients reconnect automatically. Tokens remain valid only if `JWT_SECRET_KEY` is set |
| Backend down past a deadline | On restart the scheduler auto-submits every expired attempt using saved answers |
| Database unavailable | Requests fail with 5xx; `/health` reports `DEGRADED` (503) so the platform's health check can route or restart |

## 7. Scaling out: single instance vs. multiple instances

The current build is correct on **one backend instance**. Everything that must be consistent lives in PostgreSQL, so most of the system already scales horizontally. Two components are per-process:

1. **WebSocket registry (`RealtimeHub`)** — each instance only knows its own connections. With N instances behind a load balancer, an event published on instance A would not reach a student connected to instance B. To scale out, publish events to a shared bus — e.g. **Redis Pub/Sub** (or PostgreSQL `LISTEN/NOTIFY`) — and have every instance forward bus messages to its local sockets. Participant presence should then be stored in Redis (a set per assignment with TTL heartbeats) rather than in memory.
2. **Rate limiter** — counts per instance; move to Redis (`INCR` + `EXPIRE`) for a global limit.

Other considerations for multiple instances:

- **Scheduler**: running on every instance is *safe* (row locks make finalization exactly-once) but redundant; use ShedLock or a single leader to avoid duplicate work.
- **Sticky sessions are not required**: REST is stateless (JWT) and WebSocket clients recover via snapshots on reconnect.
- **Database**: the hot paths are keyed lookups and small upserts on `answers (attempt_id, question_id)`. Connection pooling (Hikari) and, if needed, a PgBouncer in front of PostgreSQL handle large concurrent classes. Read-heavy admin screens (results, exports) could move to a read replica.

## 8. Security in the distributed setting

- JWTs carry only the user id and role; on every request the server reloads the user, so deactivation and role changes apply immediately across instances.
- WebSocket clients authenticate with their first message (not the URL, keeping tokens out of logs) and are authorized per assignment: admins must own it, students must be eligible. Unauthorized sockets are closed with `4401`/`4403`.
- Events never contain answer keys or tokens. Student-facing snapshots omit other students' details; proctoring events go only to admins.
- CORS and allowed WebSocket origins are restricted to `FRONTEND_ORIGIN`. Production must use HTTPS and WSS.

## 9. Manual concurrency test (performed)

The PRD's manual test was executed against the running application (Spring Boot + PostgreSQL + Vite, driven by Playwright in Microsoft Edge):

1. Imported six students across CSE/ECE, years II–III, sections A/B through the roster import UI.
2. Created `III-CSE` (dynamic academic, 4 members) and `III-DistributedandCloudComputing` (course-based, 3 members).
3. Created a quiz, imported questions from the template (one invalid row was reported and explicitly skipped), added one manually, published.
4. Assigned it to `III-CSE` as a live session with monitoring (threshold 2).
5. An ECE student saw no quizzes; CSE students saw the assignment.
6. Three students joined the waiting room concurrently; the admin monitor showed 3 connected; the admin started the session and all three waiting rooms opened via WebSocket.
7. Students answered concurrently; two submitted at the same moment; the admin ended the session and the third was auto-submitted with saved answers. Stored scores: 6/6, 1/6, 3/6; the fourth CSE student appeared as `NOT_SUBMITTED`.
8. Two focus-loss events flagged the first student; the admin reviewed them in the timeline.
9. The exported marksheet mapped scores to the correct register numbers and included the non-submitter.
10. A student reloaded mid-quiz: saved answers were restored and the timer continued (09:51 → 09:49), not reset.

The automated suite (`./mvnw test`) repeats the concurrency-critical parts: five students submitting in parallel, six concurrent submits of one attempt, and four concurrent session starts.
