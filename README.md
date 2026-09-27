# QuizSphere — Distributed Online Quiz Platform

QuizSphere lets faculty manage student rosters, courses and groups, author MCQ quizzes and question banks, assign quizzes to the right students, run live sessions, review browser-monitoring signals, and export Excel marksheets. Students sign in with their register number, see only the quizzes assigned to them, take quizzes with autosave and reconnect recovery, and view results once released.

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5 (Web, Security, Data JPA, Validation, WebSocket), Flyway, Apache POI, JWT |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4, shadcn-style components (Radix), React Router |
| Database | PostgreSQL (17 locally; Neon or any managed PostgreSQL in production) |
| Real-time | Spring WebSocket (raw WebSocket, JSON events) |

```text
.
├── backend/                  Spring Boot API (Maven wrapper, Flyway migrations, tests)
│   └── src/main/java/com/quizsphere/{config,controller,service,repository,entity,dto,security,websocket,util}
├── frontend/                 React + Vite app  (src/{components,pages,layouts,hooks,lib,types})
├── docs/DISTRIBUTED_SYSTEMS.md
├── docker-compose.yml        Local PostgreSQL
├── render.yaml               Render blueprint (API + static site)
└── .env.example
```

## Prerequisites

- **JDK 21+** (the build targets Java 21; newer JDKs work). Maven is not required — use `./mvnw`.
- **Node.js 20+** and npm.
- **PostgreSQL**: Docker Desktop (for `docker compose`) or any PostgreSQL 14+ instance.
- Docker is also required to run the backend test suite (Testcontainers starts a throwaway PostgreSQL).

## Run locally

```bash
# 1. Database
docker compose up -d                      # postgres://quizsphere:quizsphere@localhost:5432/quizsphere

# 2. Backend  (http://localhost:8080, Swagger UI at /swagger-ui.html)
cd backend
export JWT_SECRET_KEY="$(openssl rand -base64 48)"
export BOOTSTRAP_ADMIN_EMAIL=admin@example.edu BOOTSTRAP_ADMIN_PASSWORD='choose-a-strong-password'
export ADMIN_INVITE_CODE=choose-an-invite-code   # optional: lets other faculty self-register
./mvnw spring-boot:run

# 3. Frontend (http://localhost:5173)
cd frontend
cp .env.example .env.local                # VITE_API_BASE_URL / VITE_WS_BASE_URL
npm install
npm run dev
```

**Windows PowerShell** (5.1 has no `&&`; run each line separately). `run-dev.ps1` uses `JAVA_HOME` if it is JDK 21+, otherwise it finds an installed JDK 21+ automatically:

```powershell
docker compose up -d
cd backend
.\run-dev.ps1 -AdminEmail admin@example.edu -AdminPassword 'choose-a-strong-password'   # admin args only needed the first time
# in a second terminal
cd frontend
npm install
npm run dev
```

Sign in at `http://localhost:5173/login?role=admin` with the bootstrap admin. There are no built-in credentials: if `BOOTSTRAP_ADMIN_*` is unset and no admin exists, register one at `/register?role=admin` using `ADMIN_INVITE_CODE`.

> If your machine has an unrelated global `DATABASE_URL` (for example `sqlite:///...` from another project), the backend ignores non-PostgreSQL values with a warning and uses the local default.

### Database and migrations

Flyway applies `backend/src/main/resources/db/migration/V1__init_schema.sql` automatically on startup; Hibernate then validates the mapping (`ddl-auto: validate`). Add schema changes as new `V2__...sql` files — never edit an applied migration. All timestamps are `timestamptz` in UTC.

### Environment variables

See [`.env.example`](.env.example). The important ones:

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | JDBC URL or `postgres://user:pass@host/db?sslmode=require` (converted automatically) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Credentials when `DATABASE_URL` is a JDBC URL without them |
| `JWT_SECRET_KEY` | Token signing secret. **Required** in every shared environment; if unset, a random per-process key is used and tokens stop working after a restart |
| `FRONTEND_ORIGIN` | Comma-separated allowed origins for CORS and WebSocket |
| `ADMIN_INVITE_CODE` | Enables admin self-registration with this code; empty disables it |
| `STUDENT_SELF_REGISTRATION_ENABLED` | `true`/`false` |
| `BOOTSTRAP_ADMIN_EMAIL` / `_PASSWORD` / `_NAME` | Creates the first admin at startup if none exists |
| `QUIZ_GRACE_SECONDS` | Network-latency grace for saves/submits after the deadline (default 5) |
| `VITE_API_BASE_URL` / `VITE_WS_BASE_URL` | Frontend build-time API and WebSocket URLs (`https://` / `wss://` in production) |

## Tests

```bash
cd backend && ./mvnw test        # 37 integration tests against real PostgreSQL (needs Docker)
cd frontend && npm run build     # type-check + production build
```

The suite covers authentication and protected routes, unique register numbers and roster import, course enrollment, dynamic and manual groups, publishing and question-import validation, group/individual/code/open access rules, attempt limits, live-session transitions (including concurrent starts), deadlines and auto-submit, answer saving with stale-retry protection, scoring, idempotent and concurrent submission, five students submitting concurrently, result-release privacy, Excel content/authorization/formula-injection, proctoring capture and review, WebSocket authorization/snapshots/events, and the health endpoint.

## Using the platform

### 1. Import students
**Students → Import roster.** Download the template (.xlsx or .csv), fill it, and upload. Columns: `Register Number, Student Name, Email, Academic Year, Department, Section, Semester, Course Codes, Program, Password` (the first two, Academic Year and Department are required; Academic Year accepts `3` or `III`; course codes are separated by `;`).

The preview validates every row (duplicates in the file, bad emails, unknown course codes, …) and nothing is saved until you confirm. Existing register numbers are **skipped** unless you tick *Update students that already exist*; invalid rows block the import unless you explicitly choose to skip them. Without a Password column value, a student's initial password is their register number and they must change it at first login. Students can also be added one at a time, edited, deactivated, or self-register (if enabled).

### 2. Courses and groups
Create courses under **Courses** and enroll students there or via the roster's Course Codes column.

Under **Groups → New group**, pick the type and membership:
- **Academic** (year / department / section / program / semester), e.g. `III-CSE`, `II-ECE-A`, `IV-CSE-IT`.
- **Course-based** (enrollment, optionally combined with academic filters), e.g. `III-CSE-DCC`.
- **Custom**.
- **Dynamic** membership is recomputed from current student attributes/enrollments; **Manual** membership is managed explicitly (optionally seeded from the filter). The dialog shows a live member preview before saving. A student can belong to many groups; duplicate memberships are ignored.

### 3. Questions
Create a quiz under **Quizzes**, then add questions manually, **Import** from the template, or copy from a **Question bank**. Template columns:

| Question | Option A | Option B | Option C | Option D | Correct Answer | Marks | Explanation |
|---|---|---|---|---|---|---|---|
| What is JVM? | Java Virtual Machine | Java Variable Method | Java Visual Mode | Java Version Manager | A | 1 | Optional |

Options A and B are required, C and D optional; the answer is a letter; marks are positive numbers (up to 2 decimals). The preview lists every invalid row with reasons. Copies from a bank are independent, so editing a bank never changes a quiz. **Publish** validates the quiz (at least one question, 2–6 distinct options, exactly one correct answer, positive marks) and locks it; it can return to draft only until someone attempts it.

### 4. Assignments
**Assignments → New assignment** (or **Assign** on a published quiz):
- **Groups** — one or more groups. **Individual** — register numbers. **Join code** — a 6-character code; it only works for the selected groups/students unless *open access* is ticked. **Open** — every active student (requires explicit confirmation).
- Available-from, deadline, duration, max attempts (best attempt counts), results release (manual / after deadline / immediate), leaderboard, live session, and monitoring settings.

With **Live session** on, students wait in a waiting room; **Start session** opens the quiz for everyone and sets the official end time; **End session** auto-submits running attempts. The **Monitor** tab updates live (connected students, progress, monitoring feed).

### 5. Proctoring review
With browser monitoring enabled, the quiz page records focus loss/return and (optionally) fullscreen exits; students see a notice before starting and warnings as configured. Attempts reaching the warning threshold are flagged for review.

**Auto-submit policy:** on the **3rd** focus-loss or fullscreen-exit event, the attempt is submitted automatically (status `AUTO_SUBMITTED`). This applies to every assignment with monitoring enabled. Returning to the page never counts. Only the answers already saved are graded — nothing is deducted. Students are told about the rule before they start, must tick a consent box, and see "n of 3" warnings during the quiz.

Review attempts under **Monitoring review**: per-student timelines, mark events reviewed or follow-up required, add notes. **These are signals, not proof** — a notification or an accidental click can also move focus, so review before drawing conclusions.

### 6. Results and Excel export
**Results** lists every assigned student (including `NOT_SUBMITTED`) with scores; open any attempt for a per-question review. **Release results** makes scores, correct answers and explanations visible to students.

**Export** downloads `.xlsx` workbooks generated from the database: *Marksheet* (Register Number, Student Name, Year, Department, Section, Group/Course, Quiz, Assignment, Maximum Marks, Marks Obtained, Correct Answers, Total Questions, Status, Started At, Submitted At), *Detailed Responses* and *Summary*. Options: blank or zero marks for non-submitters, limit to one group (group marksheet), include responses. **Exports** builds a consolidated sheet across several assignments; each quiz page has a quiz-wide marksheet. Headers are frozen with filters, timestamps use your browser's time zone, and cells that start with `= + - @` are neutralized against formula injection.

## Deployment

Deploying to the public internet makes QuizSphere reachable from any phone or laptop by URL. The repository includes a Render blueprint ([`render.yaml`](render.yaml)) and a backend [`Dockerfile`](backend/Dockerfile). Free tiers of GitHub, Neon and Render are enough for a class.

1. **Put the code on GitHub.** Create an empty repository on github.com, then from the project folder:

   ```powershell
   git add .
   git commit -m "QuizSphere"
   git branch -M main
   git remote add origin https://github.com/<you>/quizsphere.git
   git push -u origin main
   ```

   `.gitignore` already excludes `.env` files, `node_modules` and build output. Never commit real passwords or secrets.
2. **Create the database (Neon).** Sign up at neon.tech, create a project, and copy the connection string (`postgres://user:pass@…neon.tech/neondb?sslmode=require`). The backend converts this format to JDBC automatically, and Flyway creates the tables on first start.
3. **Create the services (Render).** At dashboard.render.com choose **New → Blueprint** and pick your repository. Render reads `render.yaml` and proposes two services: `quizsphere-api` (Docker) and `quizsphere-web` (static site). Fill in:

   | Service | Variable | Value |
   |---|---|---|
   | quizsphere-api | `DATABASE_URL` | the Neon connection string |
   | quizsphere-api | `FRONTEND_ORIGIN` | `https://quizsphere-web.onrender.com` (the static site's URL) |
   | quizsphere-api | `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | your first admin login |
   | quizsphere-api | `ADMIN_INVITE_CODE` | a code you give to other teachers (optional) |
   | quizsphere-web | `VITE_API_BASE_URL` | `https://quizsphere-api.onrender.com` |
   | quizsphere-web | `VITE_WS_BASE_URL` | `wss://quizsphere-api.onrender.com` |

   `JWT_SECRET_KEY` is generated for you. Service URLs appear on each service's page; if Render assigns a different name, update `FRONTEND_ORIGIN` / `VITE_*` to match and redeploy the affected service (the frontend reads `VITE_*` at build time, so it must be rebuilt after changing them).
4. **Check it.** Open `https://quizsphere-api.onrender.com/health` (expect `"status":"UP"`), then the frontend URL, and sign in with the bootstrap admin. Then delete `BOOTSTRAP_ADMIN_PASSWORD` from the API's environment.
5. **Share the frontend URL** with students and teachers. It works in any modern browser on phones, tablets and laptops.

Vercel works for the frontend too: import the repo with root directory `frontend/` and set the two `VITE_*` variables (`vercel.json` handles page routing).

**Before a live exam:** Render's free web service sleeps after about 15 minutes without traffic, and the first request then takes about a minute. Open the site a few minutes before the quiz starts, or switch `quizsphere-api` to a paid instance for exam days. Deadlines are enforced on the server either way, and saved answers are never lost.

Use HTTPS/WSS only, keep secrets in the host's environment settings, and set `API_DOCS_ENABLED=false` if you don't want Swagger exposed.

## Security, privacy and limitations

- Passwords are hashed with BCrypt; JWTs are validated on every request against the current account (deactivation takes effect immediately). Roles and ownership are enforced on the server: faculty only see and manage their own quizzes, banks and assignments.
- Scores, identities, roles and timers are never taken from the client. Correct answers never reach the browser before results are released.
- Login, registration and join-code attempts are rate-limited. Errors never include stack traces.
- Monitoring stores only the event type, server time and minimal metadata (visibility state, time away). The auto-submit after 3 page-leave events is an explicit, disclosed policy; it ends the attempt but never deducts marks. No camera, microphone or screen capture is used; webcam mode is not implemented. Browsers cannot detect every application switch, and QuizSphere does not claim to be cheat-proof.
- **Retention**: attempts, answers and monitoring events are kept until an administrator deletes the related assignment/quiz or student. Institutions should define a retention period (e.g. one academic year after results are final) and purge older records.
- Single-instance assumptions (in-memory WebSocket registry and rate limiter) are described in [docs/DISTRIBUTED_SYSTEMS.md](docs/DISTRIBUTED_SYSTEMS.md).
