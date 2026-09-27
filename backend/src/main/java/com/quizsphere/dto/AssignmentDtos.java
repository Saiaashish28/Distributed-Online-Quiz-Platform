package com.quizsphere.dto;

import com.quizsphere.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AssignmentDtos {

    private AssignmentDtos() {
    }

    public record ProctoringSettings(
            boolean enabled,
            @Min(1) @Max(100) Integer warningThreshold,
            boolean showWarnings,
            boolean flagForReview,
            boolean requireFullscreen) {

        public static ProctoringSettings of(Assignment a) {
            return new ProctoringSettings(a.isProctoringEnabled(), a.getProctoringWarningThreshold(),
                    a.isProctoringShowWarnings(), a.isProctoringFlagForReview(), a.isProctoringRequireFullscreen());
        }
    }

    public record AssignmentRequest(
            @NotNull Long quizId,
            @NotBlank @Size(max = 200) String name,
            @NotNull AssignmentType type,
            List<Long> groupIds,
            List<Long> studentIds,
            List<@Size(max = 40) String> registerNumbers,
            boolean codeOpenAccess,
            Instant availableFrom,
            Instant deadline,
            @Min(1) @Max(600) Integer durationMinutes,
            @Min(1) @Max(10) Integer maxAttempts,
            @NotNull ReleaseMode resultsReleaseMode,
            boolean leaderboardEnabled,
            boolean liveSession,
            @Valid ProctoringSettings proctoring) {
    }

    public record AssignmentUpdateRequest(
            @Size(min = 1, max = 200) String name,
            List<Long> groupIds,
            List<Long> studentIds,
            List<@Size(max = 40) String> registerNumbers,
            Boolean codeOpenAccess,
            Instant availableFrom,
            Instant deadline,
            Boolean clearAvailableFrom,
            Boolean clearDeadline,
            @Min(1) @Max(600) Integer durationMinutes,
            @Min(1) @Max(10) Integer maxAttempts,
            ReleaseMode resultsReleaseMode,
            Boolean leaderboardEnabled,
            @Valid ProctoringSettings proctoring,
            AssignmentStatus status) {
    }

    public record GroupRef(Long id, String name, GroupType groupType, MembershipMode membershipMode) {
        public static GroupRef of(StudentGroup g) {
            return new GroupRef(g.getId(), g.getName(), g.getGroupType(), g.getMembershipMode());
        }
    }

    public record AssignmentSummary(Long id, String name, Long quizId, String quizTitle, StudentDtos.CourseRef course,
                                    AssignmentType type, String joinCode, Instant availableFrom, Instant deadline,
                                    int durationMinutes, int maxAttempts, AssignmentStatus status,
                                    boolean liveSession, SessionState sessionState, ReleaseMode resultsReleaseMode,
                                    boolean resultsVisible, boolean proctoringEnabled, Instant createdAt) {
    }

    public record ProgressCounts(int eligible, int notStarted, int inProgress, int submitted, int autoSubmitted,
                                 int connected) {
    }

    public record AssignmentDetail(AssignmentSummary summary, boolean codeOpenAccess, boolean leaderboardEnabled,
                                   boolean resultsReleased, Instant resultsReleasedAt,
                                   Instant sessionStartedAt, Instant sessionEndsAt, Instant sessionEndedAt,
                                   ProctoringSettings proctoring, List<GroupRef> groups,
                                   List<StudentDtos.StudentRef> students, ProgressCounts progress,
                                   int questionCount, BigDecimal totalMarks, boolean hasAttempts, Instant serverTime) {
    }

    public record ResultRow(Long studentId, String registerNumber, String fullName, Integer academicYear,
                            String department, String section, Long attemptId, int attemptsUsed, String status,
                            BigDecimal score, BigDecimal maximumScore, Integer correctCount, Integer questionCount,
                            Instant startedAt, Instant submittedAt, int warningCount, boolean flagged) {
    }

    public record ResultStats(int eligible, int submitted, int autoSubmitted, int inProgress, int notStarted,
                              BigDecimal average, BigDecimal highest, BigDecimal lowest, BigDecimal maximumScore) {
    }

    public record ResultsResponse(AssignmentSummary assignment, boolean resultsReleased, ResultStats stats,
                                  List<ResultRow> rows) {
    }

    public record ReviewedQuestion(Long questionId, int number, String text, BigDecimal points,
                                   List<ReviewedOption> options, Long selectedOptionId, Boolean correct,
                                   BigDecimal marksAwarded, String explanation, Instant answeredAt) {
    }

    public record ReviewedOption(Long id, String text, boolean correct) {
    }

    public record AdminAttemptDetail(Long attemptId, StudentDtos.StudentRef student, String assignmentName,
                                     String quizTitle, AttemptStatus status, int attemptNumber, Instant startedAt,
                                     Instant endsAt, Instant submittedAt, BigDecimal score, BigDecimal maximumScore,
                                     Integer correctCount, int questionCount, int warningCount, boolean flagged,
                                     List<ReviewedQuestion> questions) {
    }
}
