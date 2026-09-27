package com.quizsphere.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "proctoring_events")
@Getter
@Setter
@NoArgsConstructor
public class ProctoringEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private ProctoringEventType eventType;

    /** Server receive time (authoritative). */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "client_occurred_at")
    private Instant clientOccurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "warning_count", nullable = false)
    private int warningCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_notes")
    private String reviewNotes;
}
