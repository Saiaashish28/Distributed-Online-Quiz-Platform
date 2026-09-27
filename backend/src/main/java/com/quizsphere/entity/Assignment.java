package com.quizsphere.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "assignments")
@Getter
@Setter
@NoArgsConstructor
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentType type;

    @Column(name = "join_code", unique = true)
    private String joinCode;

    /** For CODE assignments: any active student with the code may join (explicit opt-in). */
    @Column(name = "code_open_access", nullable = false)
    private boolean codeOpenAccess;

    @Column(name = "available_from")
    private Instant availableFrom;

    private Instant deadline;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "results_release_mode", nullable = false)
    private ReleaseMode resultsReleaseMode = ReleaseMode.MANUAL;

    @Column(name = "results_released", nullable = false)
    private boolean resultsReleased;

    @Column(name = "results_released_at")
    private Instant resultsReleasedAt;

    @Column(name = "leaderboard_enabled", nullable = false)
    private boolean leaderboardEnabled;

    /** When true the admin controls the session (WAITING -> LIVE -> ENDED). */
    @Column(name = "live_session", nullable = false)
    private boolean liveSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_state")
    private SessionState sessionState;

    @Column(name = "session_started_at")
    private Instant sessionStartedAt;

    @Column(name = "session_ends_at")
    private Instant sessionEndsAt;

    @Column(name = "session_ended_at")
    private Instant sessionEndedAt;

    @Column(name = "proctoring_enabled", nullable = false)
    private boolean proctoringEnabled;

    @Column(name = "proctoring_warning_threshold", nullable = false)
    private int proctoringWarningThreshold = 3;

    @Column(name = "proctoring_show_warnings", nullable = false)
    private boolean proctoringShowWarnings = true;

    @Column(name = "proctoring_flag_for_review", nullable = false)
    private boolean proctoringFlagForReview = true;

    @Column(name = "proctoring_require_fullscreen", nullable = false)
    private boolean proctoringRequireFullscreen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    @ManyToMany
    @JoinTable(name = "assignment_groups",
            joinColumns = @JoinColumn(name = "assignment_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<StudentGroup> groups = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "assignment_students",
            joinColumns = @JoinColumn(name = "assignment_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id"))
    private Set<Student> students = new HashSet<>();

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
