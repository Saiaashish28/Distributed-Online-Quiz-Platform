package com.quizsphere.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "attempts")
@Getter
@Setter
@NoArgsConstructor
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(precision = 8, scale = 2)
    private BigDecimal score;

    @Column(name = "maximum_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal maximumScore;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "proctoring_warning_count", nullable = false)
    private int proctoringWarningCount;

    @Column(name = "proctoring_flagged", nullable = false)
    private boolean proctoringFlagged;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
