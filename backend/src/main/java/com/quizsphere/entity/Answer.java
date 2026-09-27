package com.quizsphere.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "answers")
@Getter
@Setter
@NoArgsConstructor
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "option_id")
    private Long optionId;

    @Column(name = "client_seq", nullable = false)
    private long clientSeq;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "marks_awarded", precision = 6, scale = 2)
    private BigDecimal marksAwarded;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
