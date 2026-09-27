package com.quizsphere.dto;

import com.quizsphere.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class StudentQuizDtos {

    private StudentQuizDtos() {
    }

    /** Student-facing dashboard status (PRD section 6). */
    public enum DashboardStatus {
        UPCOMING, AVAILABLE, IN_PROGRESS, SUBMITTED, AUTO_SUBMITTED, EXPIRED, RESULTS_RELEASED
    }

    public record StudentProctoring(boolean enabled, int warningThreshold, boolean showWarnings,
                                    boolean requireFullscreen, int autoSubmitWarnings) {
        public static StudentProctoring of(Assignment a) {
            return new StudentProctoring(a.isProctoringEnabled(), a.getProctoringWarningThreshold(),
                    a.isProctoringShowWarnings(), a.isProctoringRequireFullscreen(),
                    com.quizsphere.service.ProctoringService.AUTO_SUBMIT_WARNINGS);
        }
    }

    public record StudentAssignmentView(Long id, String name, String quizTitle, String description,
                                        String instructions, StudentDtos.CourseRef course, AssignmentType type,
                                        Instant availableFrom, Instant deadline, int durationMinutes,
                                        int maxAttempts, int attemptsUsed, DashboardStatus status, boolean canStart,
                                        String blockedReason, boolean liveSession, SessionState sessionState,
                                        Long inProgressAttemptId, Long latestFinalAttemptId, boolean resultsVisible,
                                        int questionCount, BigDecimal totalMarks, StudentProctoring proctoring,
                                        boolean leaderboardEnabled, Instant serverTime) {
    }

    public record JoinRequest(@NotBlank @Size(max = 12) String code) {
    }

    public record StudentOption(Long id, String text) {
    }

    public record StudentQuestion(Long id, int number, String text, BigDecimal points, List<StudentOption> options) {
    }

    public record SavedAnswer(Long questionId, Long optionId, long seq, Instant updatedAt) {
    }

    public record AttemptView(Long attemptId, Long assignmentId, String assignmentName, String quizTitle,
                              String instructions, StudentDtos.StudentRef student, AttemptStatus status,
                              int attemptNumber, Instant startedAt, Instant endsAt, Instant serverTime,
                              List<StudentQuestion> questions, List<SavedAnswer> answers,
                              StudentProctoring proctoring, int warningCount) {
    }

    public record AnswerItem(@NotNull Long questionId, Long optionId, @NotNull @PositiveOrZero Long seq) {
    }

    public record SaveAnswersRequest(@NotEmpty @Size(max = 200) List<@Valid AnswerItem> answers) {
    }

    public record SaveAnswersResponse(Instant savedAt, List<SavedAnswer> answers, Instant endsAt, Instant serverTime) {
    }

    public record SubmitResponse(Long attemptId, AttemptStatus status, Instant submittedAt, boolean resultsVisible,
                                 int answeredCount, int questionCount) {
    }

    public record ResultView(Long attemptId, Long assignmentId, String assignmentName, String quizTitle,
                             AttemptStatus status, int attemptNumber, Instant submittedAt, boolean released,
                             BigDecimal score, BigDecimal maximumScore, Integer correctCount, int questionCount,
                             BigDecimal percentage, boolean leaderboardEnabled,
                             List<AssignmentDtos.ReviewedQuestion> questions) {
    }

    public record LeaderboardEntry(int rank, String name, BigDecimal score, BigDecimal maximumScore,
                                   Instant submittedAt, boolean me) {
    }

    public record ProctoringEventRequest(
            @NotNull ProctoringEventType eventType,
            Instant clientTimestamp,
            Map<String, Object> metadata) {
    }

    public record ProctoringEventResponse(int warningCount, int warningThreshold, boolean showWarning,
                                          boolean flagged, boolean autoSubmitted, String message) {
    }
}
