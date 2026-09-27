package com.quizsphere.dto;

import com.quizsphere.entity.Question;
import com.quizsphere.entity.QuestionOption;
import com.quizsphere.entity.QuizStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class QuizDtos {

    private QuizDtos() {
    }

    public record QuizRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @Size(max = 5000) String instructions,
            Long courseId,
            @NotNull @Min(1) @Max(600) Integer durationMinutes,
            boolean shuffleQuestions,
            boolean shuffleOptions) {
    }

    public record QuizUpdateRequest(
            @Size(min = 1, max = 200) String title,
            @Size(max = 5000) String description,
            @Size(max = 5000) String instructions,
            Long courseId,
            Boolean clearCourse,
            @Min(1) @Max(600) Integer durationMinutes,
            Boolean shuffleQuestions,
            Boolean shuffleOptions) {
    }

    public record QuizSummary(Long id, String title, StudentDtos.CourseRef course, int durationMinutes,
                              QuizStatus status, long questionCount, BigDecimal totalPoints,
                              Instant updatedAt, Instant publishedAt) {
    }

    public record OptionAdmin(Long id, String text, boolean correct) {
        public static OptionAdmin of(QuestionOption o) {
            return new OptionAdmin(o.getId(), o.getText(), o.isCorrect());
        }
    }

    public record QuestionAdmin(Long id, String text, BigDecimal points, int position, String explanation,
                                List<OptionAdmin> options, Long sourceQuestionId) {
        public static QuestionAdmin of(Question q) {
            return new QuestionAdmin(q.getId(), q.getText(), q.getPoints(), q.getPosition(), q.getExplanation(),
                    q.getOptions().stream().map(OptionAdmin::of).toList(), q.getSourceQuestionId());
        }
    }

    public record QuizDetail(Long id, String title, String description, String instructions,
                             StudentDtos.CourseRef course, int durationMinutes, QuizStatus status,
                             boolean shuffleQuestions, boolean shuffleOptions, List<QuestionAdmin> questions,
                             BigDecimal totalPoints, boolean editable, boolean hasAssignments,
                             Instant createdAt, Instant updatedAt, Instant publishedAt) {
    }

    public record OptionRequest(@NotBlank @Size(max = 1000) String text, boolean correct) {
    }

    public record QuestionRequest(
            @NotBlank @Size(max = 4000) String text,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal points,
            @Size(max = 4000) String explanation,
            @NotNull @Size(min = 2, max = 6, message = "must have 2 to 6 options") List<@Valid OptionRequest> options) {
    }

    public record ReorderRequest(@NotEmpty List<Long> questionIds) {
    }

    public record ValidationProblem(Long questionId, Integer position, String message) {
    }

    public record QuestionImportRow(int rowNumber, String question, List<String> options, String correctAnswer,
                                    BigDecimal marks, String explanation, List<String> errors, List<String> warnings) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    public record QuestionImportResult(String fileName, boolean committed, int totalRows, int validRows,
                                       int invalidRows, int imported, List<String> headerErrors,
                                       List<QuestionImportRow> rows) {
    }

    public record BankRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description,
            Long courseId) {
    }

    public record BankSummary(Long id, String name, String description, StudentDtos.CourseRef course,
                              long questionCount, Instant createdAt) {
    }

    public record BankDetail(BankSummary bank, List<QuestionAdmin> questions) {
    }

    public record CopyToQuizRequest(@NotNull Long quizId, @NotEmpty List<Long> questionIds) {
    }

    public record CopyResult(int copied) {
    }
}
