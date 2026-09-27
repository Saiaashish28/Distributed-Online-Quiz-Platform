package com.quizsphere.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    private String instructions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuizStatus status = QuizStatus.DRAFT;

    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions;

    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<Question> questions = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
