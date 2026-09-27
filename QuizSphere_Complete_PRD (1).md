# QuizSphere — Complete PRD for Claude Code

**Project:** Distributed Online Quiz Platform (DCC Mini Project)  
**Backend:** Java 21 + Spring Boot 3.x  
**Frontend:** React + TypeScript + Vite + Tailwind CSS  
**Database:** PostgreSQL  
**Real-time:** Spring WebSocket  
**Excel:** Apache POI (`.xlsx`)  
**Migrations:** Flyway  
**Student identity:** Unique register number

> **Claude Code:** Read this document fully. Inspect the repository, outline a phased plan, then implement the real application. Use Java + Spring Boot—not Python/FastAPI. Keep the project runnable, test each phase, and do not replace functionality with mockups.

---

## 1. Overview

QuizSphere lets administrators manage students, courses, academic groups, question banks, quizzes, assignments, live sessions, results, proctoring events, and Excel exports. Students log in using their register number, access only eligible quizzes, submit answers, and view released results.

### Roles
- **Admin/Faculty:** Manage students, groups, courses, questions, quizzes, assignments, sessions, results, proctoring review, and exports.
- **Student:** Log in, view assigned quizzes, join eligible code-based quizzes, answer questions, submit, and view own released results.

## 2. Technology

### Frontend
React, TypeScript, Vite, Tailwind CSS, shadcn/ui, React Router, Fetch/Axios, WebSocket client.

### Backend
Java 21, Spring Boot 3.x, Spring Web, Spring Security, Spring Data JPA/Hibernate, Bean Validation, Spring WebSocket, Maven, Apache POI, Flyway.

### Hosting
Frontend on Vercel/Render Static Site; backend on Render or another Java-compatible host; PostgreSQL on Neon or managed PostgreSQL. Use HTTPS and `wss://` in production. Store secrets in environment variables.

## 3. Authentication and permissions

- Admin and student login; student login uses unique register number and password.
- Student fields: register number, full name, email (optional), academic year, department, section, semester, active status, and course enrollments.
- Hash passwords with BCrypt or equivalent.
- Enforce role and ownership checks on the backend.
- Students can access only their own records and authorized assignments.
- Never trust client-supplied scores, roles, identity, or timer values.
- Never expose correct answers before results release.
- No hardcoded production admin credentials.

## 4. Student, course, and group management

### Student roster
Admin can add/edit/search/deactivate students and import CSV/XLSX rosters. Support preview, duplicate detection, row-level validation errors, and import summary. Never silently overwrite records.

Suggested roster columns: Register Number, Student Name, Email, Academic Year, Department, Section, Semester, Course Codes.

### Courses
Admin can create/edit courses with course code, name, and semester; enroll students manually or through import; and view course rosters.

### Academic groups
Support groups derived from year, department, section, program/branch, and semester. Examples:
- `III-CSE` — all third-year CSE students.
- `II-ECE-A` — second-year ECE Section A.
- `IV-CSE-IT` — fourth-year CSE (IT).

### Course-based groups
Support groups filtered by course enrollment, optionally combined with academic attributes. Examples:
- `IV-CSE-IT-CloudComputing`
- `III-CSE-DCC`
- `III-CSE-MachineLearning`

A student may belong to multiple groups and courses. Support:
- **Dynamic groups:** Membership reflects current student attributes/course enrollment.
- **Manual groups:** Admin explicitly manages membership.
- Preview members before saving; prevent duplicate memberships; clearly show group type and membership mode.

## 5. Quiz and question management

### Quiz fields
Title, description/instructions, subject/course, duration, status (Draft/Published/Archived), owner, timestamps, and optional shuffle settings.

### Required question type
MCQ with one correct answer. Each question has text, options, correct option (backend only), marks, order, and optional explanation. Validate questions before publishing.

### Manual authoring
Admin can add/edit/delete/reorder questions, define options and correct answer, set marks, preview, save draft, and publish.

### Question bank
Admin can create reusable subject/course question banks, search/filter questions, and copy selected questions into a quiz. Changes to a bank must not unexpectedly alter an already published quiz.

### Bulk question import
Support `.xlsx` and `.csv`, with downloadable template, preview, validation, and explicit confirmation. Template columns:

| Question | Option A | Option B | Option C | Option D | Correct Answer | Marks | Explanation |
|---|---|---|---|---|---|---|---|
| What is JVM? | Java Virtual Machine | Java Variable Method | Java Visual Mode | Java Version Manager | A | 1 | Optional |
| Which protocol is connection-oriented? | UDP | TCP | HTTP | DNS | B | 2 | Optional |

Validate file type/size, headers, required fields, answer key, options, and numeric marks. Show invalid rows and reasons; never silently skip them. Import into a question bank or selected quiz. Do not send answer keys to student clients.

## 6. Quiz assignments

Support these assignment modes:

1. **Group-based:** Assign to one or more academic/course/custom groups.
2. **Individual:** Select students by register number.
3. **Code-based:** Generate unique join code; student must log in and pass eligibility checks.
4. **Open:** Available to all active registered students, only when explicitly configured.

A join code must not bypass group/individual restrictions unless the admin explicitly enables open access.

Assignment fields: quiz, title, type, selected groups/students, join code, available-from time, deadline, duration, max attempts (default 1), result release mode, proctoring settings, status.

Student dashboard shows assigned quizzes, course, instructions, availability/deadline, duration, and status: Upcoming, Available, In Progress, Submitted, Auto-Submitted, Expired, Results Released. Students must not see unauthorized assignments.

## 7. Quiz sessions and student experience

Session states: `WAITING`, `LIVE`, `ENDED`. Server controls session state and official start/end times.

Student quiz page shows quiz title, student name/register number, question navigation, options, selected-answer state, countdown, connection/save status, and submit confirmation.

- Save answer changes to backend promptly; upsert one answer per attempt/question.
- Show saved state and allow safe retries.
- Refresh/reconnect restores saved answers and remaining time.
- Server is authoritative for deadlines; client timer is display-only.
- Reject late changes/submissions according to policy.
- On expiry, finalize using saved answers, award zero for unanswered questions, and mark `AUTO_SUBMITTED`.
- Admin can open/start/end session and monitor participants/submission progress.
- Prevent conflicting or duplicate session starts.

## 8. Response recording, scoring, and results

Persist every answer in PostgreSQL: attempt, student, question, selected option, update timestamp. Persist attempts with student, assignment/quiz, start/end/submission timestamps, status, score, maximum score, correct count, and question count.

- Evaluate MCQs on backend only.
- Calculate marks using question points.
- Final submission is idempotent; retries cannot duplicate attempts or change finalized scores.
- Enforce attempt limits safely.
- Results stay hidden until release mode/time or admin action.
- Students see only their own released results; correct answers/explanations are hidden until release.
- Optional leaderboard can be enabled/disabled; deterministic ordering: score descending, submission time ascending, stable ID.

## 9. Excel marksheets and response export

Use Apache POI to generate real `.xlsx` files from database records. PostgreSQL is the source of truth; Excel is only an export.

Admin export options:
- Assignment or quiz marksheet
- Group marksheet
- Detailed responses
- Consolidated marks across selected quizzes

Marksheet columns:
Register Number, Student Name, Year, Department, Section, Group/Course, Quiz, Assignment, Maximum Marks, Marks Obtained, Correct Answers, Total Questions, Status, Started At, Submitted At.

Include every assigned student, including non-submitters. Use `NOT_SUBMITTED`; marks may be blank or zero based on an explicit export option.

Detailed response sheet: register number, name, quiz/assignment, question number/text, selected answer, correct answer (admin-only), marks awarded, maximum marks, answer timestamp, status.

Workbook sheets: `Marksheet`, `Detailed Responses`, `Summary`. Freeze headers, enable filters, set reasonable widths, format marks/timestamps, include quiz title/export time, and use a meaningful filename. Protect against spreadsheet formula injection. Only authorized admins can export.

## 10. Proctoring and exam integrity

### MVP: Browser-based proctoring
For assignments where enabled, record supported events:
- Quiz page loses focus (`blur`/visibility change).
- Student returns to page.
- Fullscreen exit if fullscreen mode is enabled and supported.

Show clear student notice and configurable warnings after focus-loss events. Store attempt ID, event type, timestamp, minimal metadata, warning count, review status, reviewer, and review time. Admin can view event timeline, counts, and mark reviewed or follow-up required.

**Proctoring events are signals, not proof of misconduct.** Do not automatically deduct marks or disqualify students based only on focus events. Browsers cannot reliably detect all cheating or application switches.

### Optional webcam mode
Only if time permits. Require explicit permission and clear disclosure; show camera-active indicator. Do not silently activate camera or record video by default. If snapshots are implemented, disclose purpose, frequency, storage, access, and retention; restrict access and deletion. No facial recognition in MVP. Document privacy and limitations.

Admin settings per assignment: enable browser monitoring, warning threshold, display warnings, flag for review, optional webcam mode, and results-release behavior.

## 11. WebSockets

Use Spring WebSocket for events such as:
`session_snapshot`, `participant_joined`, `participant_count_updated`, `session_started`, `session_ended`, `submission_progress`, `results_released`, and admin-only `proctoring_event_recorded`.

Validate authentication/session access. Never broadcast answer keys, tokens, or unnecessary private data. Reconnecting clients receive an authoritative snapshot. REST/database state must allow recovery if events are missed. In-memory connection management is acceptable for a single instance; document that multi-instance deployment needs shared messaging such as Redis Pub/Sub.

## 12. Database entities

Use JPA entities and Flyway migrations with foreign keys, indexes, constraints, transactions, and UTC timestamps.

- `users`: id, name, email, password_hash, role, created_at
- `students`: id, user_id, register_number (unique), full_name, academic_year, department, section, semester, active
- `courses`: id, course_code (unique), course_name, semester
- `student_courses`: student_id, course_id; unique pair
- `student_groups`: id, name, description, group_type (ACADEMIC/COURSE_BASED/CUSTOM), membership_mode (DYNAMIC/MANUAL), validated filter definition, owner_id, created_at
- `group_members`: group_id, student_id; unique pair
- `quizzes`: id, title, description, course_id, duration_minutes, status, owner_id, timestamps
- `question_banks`: id, name, course_id, owner_id
- `questions`: id, bank_id (optional), quiz_id, text, points, position, explanation
- `options`: id, question_id, text, is_correct
- `assignments`: id, quiz_id, name, type, unique join_code when present, available_from, deadline, duration, max_attempts, results_release_mode, proctoring_settings, status
- `assignment_groups`: assignment_id, group_id
- `assignment_students`: assignment_id, student_id
- `attempts`: id, assignment_id, student_id, started_at, ends_at, submitted_at, status, score, maximum_score
- `answers`: id, attempt_id, question_id, option_id, updated_at; unique `(attempt_id, question_id)`
- `proctoring_events`: id, attempt_id, event_type, occurred_at, minimal metadata, review_status, reviewed_by, reviewed_at, review_notes

## 13. REST API requirements

Use consistent routes, validation, status codes, and authorization. Refine routes as needed but implement equivalent capabilities.

**Auth**
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`

**Students**
- `GET/POST /api/admin/students`
- `POST /api/admin/students/import`
- `GET/PATCH /api/admin/students/{id}`

**Courses and groups**
- `GET/POST /api/admin/courses`
- `PATCH /api/admin/courses/{id}`
- `GET/POST /api/admin/groups`
- `PATCH/DELETE /api/admin/groups/{id}`
- `POST /api/admin/groups/{id}/members`
- `DELETE /api/admin/groups/{id}/members/{studentId}`
- `POST /api/admin/groups/{id}/refresh` for dynamic groups if needed

**Question banks and quizzes**
- `GET/POST /api/admin/question-banks`
- `POST /api/admin/question-banks/{id}/questions/import`
- `GET/POST /api/admin/quizzes`
- `GET/PATCH/DELETE /api/admin/quizzes/{id}`
- `POST /api/admin/quizzes/{id}/publish`
- `POST /api/admin/quizzes/{id}/questions`
- `POST /api/admin/quizzes/{id}/questions/import`
- `PATCH/DELETE /api/admin/questions/{id}`

**Assignments**
- `GET/POST /api/admin/assignments`
- `GET/PATCH /api/admin/assignments/{id}`
- `POST /api/admin/assignments/{id}/release-results`

**Student**
- `GET /api/student/assignments`
- `POST /api/student/assignments/join`
- `GET /api/student/assignments/{id}`
- `POST /api/student/assignments/{id}/start`
- `PUT /api/student/attempts/{id}/answers`
- `POST /api/student/attempts/{id}/submit`
- `GET /api/student/attempts/{id}/result`

**Results/export/proctoring**
- `GET /api/admin/assignments/{id}/results`
- `GET /api/admin/assignments/{id}/export/marksheet`
- `GET /api/admin/assignments/{id}/export/responses`
- `GET /api/admin/assignments/{id}/proctoring-events`
- `PATCH /api/admin/proctoring-events/{eventId}/review`
- `GET /api/admin/quizzes/{id}/export/marksheet`

**System:** `GET /health`

Use Spring Boot, not FastAPI. Add API documentation if practical.

## 14. UI pages

**Public:** Landing, admin login/registration, student login/registration.

**Admin:** Dashboard, student roster/import, course management, group management, question banks, quiz list/editor, question import preview, assignment creation, live monitoring, results, proctoring review, Excel exports.

**Student:** Dashboard, assigned quizzes, join by code, waiting room, quiz-taking page, submission confirmation, results.

Design must be responsive, accessible, polished, and functional, with clear status badges, loading/error/empty states, confirmation dialogs, and separate admin/student navigation. No core placeholder buttons.

## 15. Security, privacy, reliability

- Spring Security, role/ownership checks, BCrypt, server validation.
- Environment secrets and restricted production CORS.
- Rate limiting on login/join where practical.
- Server-side deadline enforcement and idempotent operations.
- Protect answer keys and marks.
- Safe spreadsheet export.
- Restrict proctoring-event access and minimize collected data.
- Document retention policy and limitations.
- Do not log passwords/tokens or return stack traces in production.
- Do not claim the platform is cheat-proof.

## 16. Testing

Automated tests must cover:
- Authentication and protected routes.
- Unique register numbers and roster import.
- Course enrollment, group filters, manual membership.
- Quiz publishing and question import validation.
- Group/individual assignment access and join-code rules.
- Attempt limits, session transitions, deadlines.
- Answer saving, scoring, idempotent submission.
- Results release and student privacy.
- Excel content and authorization.
- Proctoring event capture and review.
- Health endpoint.

### Manual concurrency test
1. Import students from different departments/sections.
2. Create `III-CSE`, `II-ECE-A`, and a course-based group.
3. Create a quiz and import questions from the template.
4. Assign the quiz to one group.
5. Test eligible and ineligible student logins.
6. Open at least three student sessions and start the quiz.
7. Submit answers concurrently and verify stored responses/scores.
8. Trigger a focus-loss event and review it in admin.
9. Export marksheet and verify register number/score mapping and non-submitters.
10. Reconnect a student and verify answer/timer recovery.

## 17. Environment and deployment

Create Maven backend, Vite frontend, Flyway migrations, `.env.example`, `.gitignore`, `README.md`, and `docs/DISTRIBUTED_SYSTEMS.md`.

Example variables:

```env
DATABASE_URL=
JWT_SECRET_KEY=
FRONTEND_ORIGIN=http://localhost:5173
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_BASE_URL=ws://localhost:8080
```

Adapt database URL to the host's JDBC format. Use secure production values and `wss://`.

README must cover prerequisites, database/migrations, environment setup, run commands, tests, student import, group creation, question template/import, assignment, proctoring, Excel export, and deployment.

## 18. Suggested repository structure

```text
quizsphere/
├── frontend/
│   └── src/{components,pages,layouts,hooks,services,types,lib}/
├── backend/
│   ├── src/main/java/.../{config,controller,service,repository,entity,dto,security,websocket}/
│   ├── src/main/resources/
│   ├── src/test/
│   └── pom.xml
├── docs/DISTRIBUTED_SYSTEMS.md
├── .env.example
├── .gitignore
└── README.md
```

## 19. Implementation phases

1. **Setup:** Scaffold frontend/backend, PostgreSQL, Flyway, health endpoint.
2. **Auth and students:** Admin/student login, profiles, roster import.
3. **Courses and groups:** Course management, academic/course-based dynamic and manual groups.
4. **Question authoring:** Question banks, manual editor, spreadsheet import and preview.
5. **Assignments:** Group, individual, code-based, open access, eligibility.
6. **Live quiz:** Sessions, timer, autosave, WebSockets, reconnect.
7. **Evaluation:** Scoring, submission, result release, leaderboard.
8. **Excel:** Marksheet, detailed responses, summary workbook.
9. **Proctoring:** Browser event logging, warnings, admin review; webcam only if feasible and privacy-compliant.
10. **Testing and deployment:** Full tests, concurrent client test, production build, deploy if credentials/access are available.

## 20. Definition of done

- Admin/student authentication works; register numbers are unique.
- Student import, courses, academic and course-based groups work.
- Admin can author questions manually or import them.
- Quiz assignment works for groups, individuals, codes, and open access.
- Multiple students can take quizzes concurrently.
- Responses persist and backend scoring is correct.
- Marks are linked to correct register numbers.
- Excel marksheets and detailed response exports work.
- Browser proctoring events can be reviewed by admins.
- Students see only their own released results.
- WebSockets and reconnect recovery work.
- Core tests pass; UI is responsive; docs are complete.
- No required workflow relies on mock data.

## 21. Instructions to Claude Code

1. Inspect repository and existing code first.
2. Use **Java 21 + Spring Boot** backend; do not use Python/FastAPI.
3. Use React + TypeScript + Vite and PostgreSQL.
4. Implement phases in order, keeping the app runnable.
5. Prioritize complete end-to-end flows over decorative polish.
6. Do not substitute mock data for required functionality.
7. Run builds/tests often and fix failures.
8. Never commit secrets.
9. Do not claim deployment succeeded unless verified; if manual credentials are needed, provide exact remaining steps.

At completion, summarize implemented features, key files, run commands, migrations, tests, environment variables, roster/group workflow, question import, assignment, proctoring review, Excel export, deployment steps, and blockers.

**Begin by inspecting the repository, then implement QuizSphere end-to-end according to this PRD.**
