package com.quizsphere.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private QuestionBank bank;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal points = BigDecimal.ONE;

    @Column(nullable = false)
    private int position;

    private String explanation;

    @Column(name = "source_question_id")
    private Long sourceQuestionId;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<QuestionOption> options = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Optional<QuestionOption> correctOption() {
        return options.stream().filter(QuestionOption::isCorrect).findFirst();
    }

    public void addOption(String text, boolean correct) {
        QuestionOption o = new QuestionOption();
        o.setQuestion(this);
        o.setText(text);
        o.setCorrect(correct);
        o.setPosition(options.size());
        options.add(o);
    }
}
