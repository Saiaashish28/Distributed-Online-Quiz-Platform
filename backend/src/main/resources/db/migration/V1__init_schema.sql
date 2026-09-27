-- QuizSphere initial schema. All timestamps are stored as timestamptz (UTC).

CREATE TABLE users (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(150) NOT NULL,
    email                VARCHAR(255),
    password_hash        VARCHAR(100) NOT NULL,
    role                 VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'STUDENT')),
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Admins log in by email, so admin emails must be unique (case-insensitive).
CREATE UNIQUE INDEX ux_users_admin_email ON users (lower(email)) WHERE role = 'ADMIN';

CREATE TABLE students (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    register_number VARCHAR(40)  NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    academic_year   INT          NOT NULL CHECK (academic_year BETWEEN 1 AND 6),
    department      VARCHAR(50)  NOT NULL,
    section         VARCHAR(20),
    program         VARCHAR(50),
    semester        INT      CHECK (semester BETWEEN 1 AND 12),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_students_register_number UNIQUE (register_number)
);
CREATE INDEX ix_students_academic ON students (academic_year, department, section);

CREATE TABLE courses (
    id          BIGSERIAL PRIMARY KEY,
    course_code VARCHAR(30)  NOT NULL,
    course_name VARCHAR(150) NOT NULL,
    semester    INT      CHECK (semester BETWEEN 1 AND 12),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_courses_code UNIQUE (course_code)
);

CREATE TABLE student_courses (
    student_id  BIGINT      NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    course_id   BIGINT      NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, course_id)
);
CREATE INDEX ix_student_courses_course ON student_courses (course_id);

CREATE TABLE student_groups (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    description       VARCHAR(500),
    group_type        VARCHAR(20)  NOT NULL CHECK (group_type IN ('ACADEMIC', 'COURSE_BASED', 'CUSTOM')),
    membership_mode   VARCHAR(20)  NOT NULL CHECK (membership_mode IN ('DYNAMIC', 'MANUAL')),
    filter_definition JSONB,
    owner_id          BIGINT       NOT NULL REFERENCES users (id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_student_groups_name UNIQUE (name)
);

CREATE TABLE group_members (
    group_id   BIGINT      NOT NULL REFERENCES student_groups (id) ON DELETE CASCADE,
    student_id BIGINT      NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, student_id)
);
CREATE INDEX ix_group_members_student ON group_members (student_id);

CREATE TABLE quizzes (
    id                BIGSERIAL PRIMARY KEY,
    title             VARCHAR(200) NOT NULL,
    description       TEXT,
    instructions      TEXT,
    course_id         BIGINT REFERENCES courses (id) ON DELETE SET NULL,
    duration_minutes  INT          NOT NULL CHECK (duration_minutes BETWEEN 1 AND 600),
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    shuffle_questions BOOLEAN      NOT NULL DEFAULT FALSE,
    shuffle_options   BOOLEAN      NOT NULL DEFAULT FALSE,
    owner_id          BIGINT       NOT NULL REFERENCES users (id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at      TIMESTAMPTZ
);
CREATE INDEX ix_quizzes_owner ON quizzes (owner_id);

CREATE TABLE question_banks (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    course_id   BIGINT REFERENCES courses (id) ON DELETE SET NULL,
    owner_id    BIGINT       NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_question_banks_owner ON question_banks (owner_id);

-- A question lives either in a bank (reusable) or in a quiz (a copy), never both.
CREATE TABLE questions (
    id                 BIGSERIAL PRIMARY KEY,
    bank_id            BIGINT REFERENCES question_banks (id) ON DELETE CASCADE,
    quiz_id            BIGINT REFERENCES quizzes (id) ON DELETE CASCADE,
    text               TEXT          NOT NULL,
    points             NUMERIC(6, 2) NOT NULL CHECK (points > 0),
    position           INT           NOT NULL DEFAULT 0,
    explanation        TEXT,
    source_question_id BIGINT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_questions_owner CHECK ((bank_id IS NULL) <> (quiz_id IS NULL))
);
CREATE INDEX ix_questions_quiz ON questions (quiz_id, position);
CREATE INDEX ix_questions_bank ON questions (bank_id);

CREATE TABLE options (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT  NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    text        TEXT    NOT NULL,
    is_correct  BOOLEAN NOT NULL DEFAULT FALSE,
    position    INT     NOT NULL DEFAULT 0
);
CREATE INDEX ix_options_question ON options (question_id);

CREATE TABLE assignments (
    id                            BIGSERIAL PRIMARY KEY,
    quiz_id                       BIGINT       NOT NULL REFERENCES quizzes (id),
    owner_id                      BIGINT       NOT NULL REFERENCES users (id),
    name                          VARCHAR(200) NOT NULL,
    type                          VARCHAR(20)  NOT NULL CHECK (type IN ('GROUP', 'INDIVIDUAL', 'CODE', 'OPEN')),
    join_code                     VARCHAR(12),
    code_open_access              BOOLEAN      NOT NULL DEFAULT FALSE,
    available_from                TIMESTAMPTZ,
    deadline                      TIMESTAMPTZ,
    duration_minutes              INT          NOT NULL CHECK (duration_minutes BETWEEN 1 AND 600),
    max_attempts                  INT          NOT NULL DEFAULT 1 CHECK (max_attempts BETWEEN 1 AND 10),
    results_release_mode          VARCHAR(20)  NOT NULL CHECK (results_release_mode IN ('IMMEDIATE', 'AFTER_DEADLINE', 'MANUAL')),
    results_released              BOOLEAN      NOT NULL DEFAULT FALSE,
    results_released_at           TIMESTAMPTZ,
    leaderboard_enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    live_session                  BOOLEAN      NOT NULL DEFAULT FALSE,
    session_state                 VARCHAR(20) CHECK (session_state IN ('WAITING', 'LIVE', 'ENDED')),
    session_started_at            TIMESTAMPTZ,
    session_ends_at               TIMESTAMPTZ,
    session_ended_at              TIMESTAMPTZ,
    proctoring_enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    proctoring_warning_threshold  INT          NOT NULL DEFAULT 3 CHECK (proctoring_warning_threshold BETWEEN 1 AND 100),
    proctoring_show_warnings      BOOLEAN      NOT NULL DEFAULT TRUE,
    proctoring_flag_for_review    BOOLEAN      NOT NULL DEFAULT TRUE,
    proctoring_require_fullscreen BOOLEAN      NOT NULL DEFAULT FALSE,
    status                        VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED')),
    version                       BIGINT       NOT NULL DEFAULT 0,
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_assignments_join_code UNIQUE (join_code),
    CONSTRAINT ck_assignments_window CHECK (available_from IS NULL OR deadline IS NULL OR deadline > available_from),
    CONSTRAINT ck_assignments_code CHECK (type <> 'CODE' OR join_code IS NOT NULL)
);
CREATE INDEX ix_assignments_quiz ON assignments (quiz_id);
CREATE INDEX ix_assignments_owner ON assignments (owner_id);

CREATE TABLE assignment_groups (
    assignment_id BIGINT NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    group_id      BIGINT NOT NULL REFERENCES student_groups (id),
    PRIMARY KEY (assignment_id, group_id)
);

CREATE TABLE assignment_students (
    assignment_id BIGINT NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    student_id    BIGINT NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    PRIMARY KEY (assignment_id, student_id)
);

-- Students who joined a code-based assignment with its join code.
CREATE TABLE assignment_joins (
    id            BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT      NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
    student_id    BIGINT      NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_assignment_joins UNIQUE (assignment_id, student_id)
);

CREATE TABLE attempts (
    id                       BIGSERIAL PRIMARY KEY,
    assignment_id            BIGINT      NOT NULL REFERENCES assignments (id),
    student_id               BIGINT      NOT NULL REFERENCES students (id),
    attempt_number           INT         NOT NULL,
    started_at               TIMESTAMPTZ NOT NULL,
    ends_at                  TIMESTAMPTZ NOT NULL,
    submitted_at             TIMESTAMPTZ,
    status                   VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'AUTO_SUBMITTED')),
    score                    NUMERIC(8, 2),
    maximum_score            NUMERIC(8, 2) NOT NULL,
    correct_count            INT,
    question_count           INT         NOT NULL,
    proctoring_warning_count INT         NOT NULL DEFAULT 0,
    proctoring_flagged       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_attempts_number UNIQUE (assignment_id, student_id, attempt_number)
);
-- At most one in-progress attempt per student per assignment.
CREATE UNIQUE INDEX ux_attempts_in_progress ON attempts (assignment_id, student_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_attempts_status_ends ON attempts (status, ends_at);
CREATE INDEX ix_attempts_student ON attempts (student_id);

CREATE TABLE answers (
    id            BIGSERIAL PRIMARY KEY,
    attempt_id    BIGINT      NOT NULL REFERENCES attempts (id) ON DELETE CASCADE,
    question_id   BIGINT      NOT NULL REFERENCES questions (id),
    option_id     BIGINT REFERENCES options (id),
    client_seq    BIGINT      NOT NULL DEFAULT 0,
    is_correct    BOOLEAN,
    marks_awarded NUMERIC(6, 2),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_answers_attempt_question UNIQUE (attempt_id, question_id)
);

CREATE TABLE proctoring_events (
    id                 BIGSERIAL PRIMARY KEY,
    attempt_id         BIGINT      NOT NULL REFERENCES attempts (id) ON DELETE CASCADE,
    event_type         VARCHAR(30) NOT NULL CHECK (event_type IN ('FOCUS_LOST', 'FOCUS_RETURNED', 'FULLSCREEN_EXIT', 'FULLSCREEN_ENTER')),
    occurred_at        TIMESTAMPTZ NOT NULL,
    client_occurred_at TIMESTAMPTZ,
    metadata           JSONB,
    warning_count      INT         NOT NULL DEFAULT 0,
    review_status      VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (review_status IN ('PENDING', 'REVIEWED', 'FOLLOW_UP_REQUIRED')),
    reviewed_by        BIGINT REFERENCES users (id),
    reviewed_at        TIMESTAMPTZ,
    review_notes       VARCHAR(1000)
);
CREATE INDEX ix_proctoring_events_attempt ON proctoring_events (attempt_id, occurred_at);
