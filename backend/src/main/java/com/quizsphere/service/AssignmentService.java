package com.quizsphere.service;

import com.quizsphere.dto.AssignmentDtos.*;
import com.quizsphere.dto.StudentDtos.CourseRef;
import com.quizsphere.dto.StudentDtos.IdsOrRegisterNumbers;
import com.quizsphere.dto.StudentDtos.StudentRef;
import com.quizsphere.entity.*;
import com.quizsphere.exception.ApiException;
import com.quizsphere.repository.*;
import com.quizsphere.security.AuthUser;
import com.quizsphere.websocket.RealtimeHub;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AssignmentService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AssignmentRepository assignmentRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final StudentGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final AttemptRepository attemptRepository;
    private final ProctoringEventRepository proctoringEventRepository;
    private final CourseService.StudentLookup studentLookup;
    private final EligibilityService eligibility;
    private final ProgressService progressService;
    private final AttemptService attemptService;
    private final RealtimeHub hub;

    @PersistenceContext
    private EntityManager entityManager;

    public AssignmentService(AssignmentRepository assignmentRepository, QuizRepository quizRepository,
                             QuestionRepository questionRepository, StudentGroupRepository groupRepository,
                             UserRepository userRepository, AttemptRepository attemptRepository,
                             ProctoringEventRepository proctoringEventRepository,
                             CourseService.StudentLookup studentLookup, EligibilityService eligibility,
                             ProgressService progressService, AttemptService attemptService, RealtimeHub hub) {
        this.assignmentRepository = assignmentRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.attemptRepository = attemptRepository;
        this.proctoringEventRepository = proctoringEventRepository;
        this.studentLookup = studentLookup;
        this.eligibility = eligibility;
        this.progressService = progressService;
        this.attemptService = attemptService;
        this.hub = hub;
    }

    // ------------------------------------------------------------------------- CRUD

    @Transactional(readOnly = true)
    public List<AssignmentSummary> list(AuthUser user) {
        Instant now = Instant.now();
        return assignmentRepository.findByOwnerIdOrderByCreatedAtDesc(user.userId()).stream()
                .map(a -> summary(a, now)).toList();
    }

    @Transactional
    public AssignmentDetail create(AssignmentRequest req, AuthUser user) {
        Quiz quiz = quizRepository.findById(req.quizId())
                .filter(q -> q.getOwner().getId().equals(user.userId()))
                .orElseThrow(() -> ApiException.notFound("Quiz"));
        if (quiz.getStatus() != QuizStatus.PUBLISHED) {
            throw ApiException.conflict("Only published quizzes can be assigned");
        }
        Assignment a = new Assignment();
        a.setQuiz(quiz);
        a.setOwner(userRepository.getReferenceById(user.userId()));
        a.setName(req.name().trim());
        a.setType(req.type());
        a.setCodeOpenAccess(req.type() == AssignmentType.CODE && req.codeOpenAccess());
        a.setAvailableFrom(req.availableFrom());
        a.setDeadline(req.deadline());
        a.setDurationMinutes(req.durationMinutes() != null ? req.durationMinutes() : quiz.getDurationMinutes());
        a.setMaxAttempts(req.maxAttempts() != null ? req.maxAttempts() : 1);
        a.setResultsReleaseMode(req.resultsReleaseMode());
        a.setLeaderboardEnabled(req.leaderboardEnabled());
        a.setLiveSession(req.liveSession());
        a.setSessionState(req.liveSession() ? SessionState.WAITING : null);
        applyProctoring(a, req.proctoring());
        applyTargets(a, req.groupIds(), req.studentIds(), req.registerNumbers());
        if (req.type() == AssignmentType.CODE) {
            a.setJoinCode(newJoinCode());
        }
        validate(a);
        assignmentRepository.saveAndFlush(a);
        return detail(a.getId(), user);
    }

    @Transactional
    public AssignmentDetail update(Long id, AssignmentUpdateRequest req, AuthUser user) {
        Assignment a = findOwned(id, user);
        if (req.name() != null) a.setName(req.name().trim());
        if (req.codeOpenAccess() != null && a.getType() == AssignmentType.CODE) a.setCodeOpenAccess(req.codeOpenAccess());
        if (Boolean.TRUE.equals(req.clearAvailableFrom())) a.setAvailableFrom(null);
        else if (req.availableFrom() != null) a.setAvailableFrom(req.availableFrom());
        if (Boolean.TRUE.equals(req.clearDeadline())) a.setDeadline(null);
        else if (req.deadline() != null) a.setDeadline(req.deadline());
        if (req.durationMinutes() != null) a.setDurationMinutes(req.durationMinutes());
        if (req.maxAttempts() != null) a.setMaxAttempts(req.maxAttempts());
        if (req.resultsReleaseMode() != null) a.setResultsReleaseMode(req.resultsReleaseMode());
        if (req.leaderboardEnabled() != null) a.setLeaderboardEnabled(req.leaderboardEnabled());
        if (req.proctoring() != null) applyProctoring(a, req.proctoring());
        if (req.status() != null) a.setStatus(req.status());
        if (req.groupIds() != null || req.studentIds() != null || req.registerNumbers() != null) {
            if (req.groupIds() != null) a.getGroups().clear();
            if (req.studentIds() != null || req.registerNumbers() != null) a.getStudents().clear();
            applyTargets(a, req.groupIds(), req.studentIds(), req.registerNumbers());
        }
        validate(a);
        assignmentRepository.saveAndFlush(a);
        progressService.publish(id);
        return detail(id, user);
    }

    @Transactional
    public void delete(Long id, AuthUser user) {
        Assignment a = findOwned(id, user);
        if (attemptRepository.existsByAssignmentId(id)) {
            throw ApiException.conflict("Students have attempted this assignment; close it instead of deleting");
        }
        assignmentRepository.delete(a);
    }

    @Transactional
    public AssignmentDetail regenerateCode(Long id, AuthUser user) {
        Assignment a = findOwned(id, user);
        if (a.getType() != AssignmentType.CODE) {
            throw ApiException.badRequest("Only code-based assignments have a join code");
        }
        a.setJoinCode(newJoinCode());
        return detail(id, user);
    }

    @Transactional(readOnly = true)
    public AssignmentDetail detail(Long id, AuthUser user) {
        Assignment a = findOwned(id, user);
        Instant now = Instant.now();
        List<Question> questions = questionRepository.findByQuizIdWithOptions(a.getQuiz().getId());
        return new AssignmentDetail(summary(a, now), a.isCodeOpenAccess(), a.isLeaderboardEnabled(),
                a.isResultsReleased(), a.getResultsReleasedAt(), a.getSessionStartedAt(), a.getSessionEndsAt(),
                a.getSessionEndedAt(), ProctoringSettings.of(a),
                a.getGroups().stream().sorted(Comparator.comparing(StudentGroup::getName)).map(GroupRef::of).toList(),
                a.getStudents().stream().sorted(Comparator.comparing(Student::getRegisterNumber)).map(StudentRef::of).toList(),
                progressService.progress(a), questions.size(),
                questions.stream().map(Question::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add),
                attemptRepository.existsByAssignmentId(id), now);
    }

    // ------------------------------------------------------------------ live session

    @Transactional
    public AssignmentDetail startSession(Long id, AuthUser user) {
        findOwned(id, user);
        Assignment a = lockFresh(id);
        if (!a.isLiveSession()) {
            throw ApiException.badRequest("This assignment is not a live session");
        }
        if (a.getSessionState() != SessionState.WAITING) {
            throw ApiException.conflict("The session has already " + (a.getSessionState() == SessionState.LIVE ? "started" : "ended"));
        }
        if (a.getStatus() == AssignmentStatus.CLOSED) {
            throw ApiException.conflict("This assignment is closed");
        }
        Instant now = Instant.now();
        if (a.getDeadline() != null && !now.isBefore(a.getDeadline())) {
            throw ApiException.conflict("The deadline has already passed");
        }
        Instant endsAt = now.plus(Duration.ofMinutes(a.getDurationMinutes()));
        if (a.getDeadline() != null && a.getDeadline().isBefore(endsAt)) endsAt = a.getDeadline();
        a.setSessionState(SessionState.LIVE);
        a.setSessionStartedAt(now);
        a.setSessionEndsAt(endsAt);
        assignmentRepository.saveAndFlush(a);
        hub.toAll(id, "session_started", Map.of("sessionState", SessionState.LIVE,
                "sessionStartedAt", now, "sessionEndsAt", endsAt));
        return detail(id, user);
    }

    @Transactional
    public AssignmentDetail endSession(Long id, AuthUser user) {
        findOwned(id, user);
        endSessionInternal(id);
        return detail(id, user);
    }

    /** Ends a live session and auto-submits every running attempt with its saved answers. */
    @Transactional
    public void endSessionInternal(Long id) {
        Assignment a = lockFresh(id);
        if (!a.isLiveSession()) {
            throw ApiException.badRequest("This assignment is not a live session");
        }
        if (a.getSessionState() == SessionState.ENDED) {
            return; // idempotent
        }
        Instant now = Instant.now();
        a.setSessionState(SessionState.ENDED);
        a.setSessionEndedAt(now);
        if (a.getSessionEndsAt() == null || a.getSessionEndsAt().isAfter(now)) {
            a.setSessionEndsAt(now);
        }
        assignmentRepository.saveAndFlush(a);
        for (Long attemptId : attemptRepository.findInProgressIdsByAssignment(id)) {
            attemptService.autoFinalize(attemptId);
        }
        hub.toAll(id, "session_ended", Map.of("sessionState", SessionState.ENDED, "sessionEndedAt", now,
                "resultsVisible", AssignmentRules.resultsVisible(a, now)));
    }

    @Transactional
    public AssignmentDetail releaseResults(Long id, AuthUser user) {
        Assignment a = findOwned(id, user);
        if (!a.isResultsReleased()) {
            a.setResultsReleased(true);
            a.setResultsReleasedAt(Instant.now());
            assignmentRepository.saveAndFlush(a);
            hub.toAll(id, "results_released", Map.of("assignmentId", id));
        }
        return detail(id, user);
    }

    // ----------------------------------------------------------------------- results

    @Transactional(readOnly = true)
    public ResultsResponse results(Long id, AuthUser user) {
        Assignment a = findOwned(id, user);
        List<ResultRow> rows = resultRows(a);
        List<ResultRow> finals = rows.stream().filter(r -> r.score() != null
                && ("SUBMITTED".equals(r.status()) || "AUTO_SUBMITTED".equals(r.status()))).toList();
        BigDecimal avg = finals.isEmpty() ? null : finals.stream().map(ResultRow::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(finals.size()), 2, RoundingMode.HALF_UP);
        BigDecimal max = questionRepository.findByQuizIdWithOptions(a.getQuiz().getId()).stream()
                .map(Question::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add);
        ResultStats stats = new ResultStats(rows.size(),
                count(rows, "SUBMITTED"), count(rows, "AUTO_SUBMITTED"), count(rows, "IN_PROGRESS"),
                count(rows, "NOT_SUBMITTED"), avg,
                finals.stream().map(ResultRow::score).max(Comparator.naturalOrder()).orElse(null),
                finals.stream().map(ResultRow::score).min(Comparator.naturalOrder()).orElse(null), max);
        return new ResultsResponse(summary(a, Instant.now()), a.isResultsReleased(), stats, rows);
    }

    private static int count(List<ResultRow> rows, String status) {
        return (int) rows.stream().filter(r -> status.equals(r.status())).count();
    }

    /** One row per assigned student, including students who never started (NOT_SUBMITTED). */
    @Transactional(readOnly = true)
    public List<ResultRow> resultRows(Assignment a) {
        Map<Long, List<Attempt>> byStudent = attemptRepository.findByAssignmentIdOrderByIdAsc(a.getId()).stream()
                .collect(Collectors.groupingBy(x -> x.getStudent().getId()));
        List<ResultRow> rows = new ArrayList<>();
        for (Student s : eligibility.assignedStudents(a)) {
            List<Attempt> attempts = byStudent.getOrDefault(s.getId(), List.of());
            Optional<Attempt> c = AssignmentRules.counted(attempts);
            int warnings = attempts.stream().mapToInt(Attempt::getProctoringWarningCount).sum();
            boolean flagged = attempts.stream().anyMatch(Attempt::isProctoringFlagged);
            rows.add(new ResultRow(s.getId(), s.getRegisterNumber(), s.getFullName(), s.getAcademicYear(),
                    s.getDepartment(), s.getSection(), c.map(Attempt::getId).orElse(null), attempts.size(),
                    c.map(x -> x.getStatus().name()).orElse("NOT_SUBMITTED"),
                    c.map(Attempt::getScore).orElse(null), c.map(Attempt::getMaximumScore).orElse(null),
                    c.map(Attempt::getCorrectCount).orElse(null), c.map(Attempt::getQuestionCount).orElse(null),
                    c.map(Attempt::getStartedAt).orElse(null), c.map(Attempt::getSubmittedAt).orElse(null),
                    warnings, flagged));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public AdminAttemptDetail attemptDetail(Long attemptId, AuthUser user) {
        Attempt at = attemptRepository.findById(attemptId).orElseThrow(() -> ApiException.notFound("Attempt"));
        Assignment a = at.getAssignment();
        if (!a.getOwner().getId().equals(user.userId())) {
            throw ApiException.notFound("Attempt");
        }
        return new AdminAttemptDetail(at.getId(), StudentRef.of(at.getStudent()), a.getName(), a.getQuiz().getTitle(),
                at.getStatus(), at.getAttemptNumber(), at.getStartedAt(), at.getEndsAt(), at.getSubmittedAt(),
                at.getScore(), at.getMaximumScore(), at.getCorrectCount(), at.getQuestionCount(),
                at.getProctoringWarningCount(), at.isProctoringFlagged(), attemptService.review(at));
    }

    // ----------------------------------------------------------------------- helpers

    /** Row-locks the assignment and reloads its state, so session transitions never act on stale data. */
    private Assignment lockFresh(Long id) {
        Assignment a = entityManager.find(Assignment.class, id);
        if (a == null) throw ApiException.notFound("Assignment");
        entityManager.refresh(a, LockModeType.PESSIMISTIC_WRITE);
        return a;
    }

    public Assignment findOwned(Long id, AuthUser user) {
        Assignment a = assignmentRepository.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Assignment"));
        if (!a.getOwner().getId().equals(user.userId())) {
            throw ApiException.notFound("Assignment");
        }
        return a;
    }

    AssignmentSummary summary(Assignment a, Instant now) {
        Quiz q = a.getQuiz();
        return new AssignmentSummary(a.getId(), a.getName(), q.getId(), q.getTitle(), CourseRef.of(q.getCourse()),
                a.getType(), a.getJoinCode(), a.getAvailableFrom(), a.getDeadline(), a.getDurationMinutes(),
                a.getMaxAttempts(), a.getStatus(), a.isLiveSession(), a.getSessionState(), a.getResultsReleaseMode(),
                AssignmentRules.resultsVisible(a, now), a.isProctoringEnabled(), a.getCreatedAt());
    }

    private void applyTargets(Assignment a, List<Long> groupIds, List<Long> studentIds, List<String> registerNumbers) {
        if (groupIds != null && !groupIds.isEmpty()) {
            Set<Long> ids = new HashSet<>(groupIds);
            List<StudentGroup> groups = groupRepository.findAllById(ids);
            if (groups.size() != ids.size()) {
                throw ApiException.badRequest("One or more selected groups do not exist");
            }
            a.getGroups().addAll(groups);
        }
        if ((studentIds != null && !studentIds.isEmpty()) || (registerNumbers != null && !registerNumbers.isEmpty())) {
            CourseService.StudentLookup.Resolved r = studentLookup.resolve(new IdsOrRegisterNumbers(studentIds, registerNumbers));
            if (!r.notFound().isEmpty()) {
                throw ApiException.badRequest("Unknown students: " + String.join(", ", r.notFound()));
            }
            a.getStudents().addAll(r.students());
        }
    }

    private static void applyProctoring(Assignment a, ProctoringSettings p) {
        if (p == null) return;
        a.setProctoringEnabled(p.enabled());
        if (p.warningThreshold() != null) a.setProctoringWarningThreshold(p.warningThreshold());
        a.setProctoringShowWarnings(p.showWarnings());
        a.setProctoringFlagForReview(p.flagForReview());
        a.setProctoringRequireFullscreen(p.requireFullscreen());
    }

    private static void validate(Assignment a) {
        if (a.getAvailableFrom() != null && a.getDeadline() != null && !a.getDeadline().isAfter(a.getAvailableFrom())) {
            throw ApiException.badRequest("The deadline must be after the available-from time");
        }
        switch (a.getType()) {
            case GROUP -> {
                if (a.getGroups().isEmpty()) throw ApiException.badRequest("Select at least one group");
            }
            case INDIVIDUAL -> {
                if (a.getStudents().isEmpty()) throw ApiException.badRequest("Select at least one student");
            }
            case CODE -> {
                if (!a.isCodeOpenAccess() && a.getGroups().isEmpty() && a.getStudents().isEmpty()) {
                    throw ApiException.badRequest("A code-based assignment needs target groups/students, or open access explicitly enabled");
                }
            }
            case OPEN -> {
                // Open to all active students: the OPEN type itself is the explicit opt-in.
            }
        }
        if (a.getResultsReleaseMode() == ReleaseMode.AFTER_DEADLINE && a.getDeadline() == null && !a.isLiveSession()) {
            throw ApiException.badRequest("'After deadline' release needs a deadline (or a live session)");
        }
    }

    private String newJoinCode() {
        for (int i = 0; i < 20; i++) {
            StringBuilder sb = new StringBuilder(6);
            for (int j = 0; j < 6; j++) sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            if (!assignmentRepository.existsByJoinCode(sb.toString())) return sb.toString();
        }
        throw new IllegalStateException("Could not generate a unique join code");
    }
}
