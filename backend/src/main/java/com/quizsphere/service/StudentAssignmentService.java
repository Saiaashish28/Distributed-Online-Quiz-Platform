package com.quizsphere.service;

import com.quizsphere.config.AppProperties;
import com.quizsphere.dto.StudentDtos.CourseRef;
import com.quizsphere.dto.StudentQuizDtos.*;
import com.quizsphere.entity.*;
import com.quizsphere.exception.ApiException;
import com.quizsphere.repository.*;
import com.quizsphere.security.RateLimiter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Student dashboard: only assignments the student is authorized to see are ever returned. */
@Service
public class StudentAssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AttemptRepository attemptRepository;
    private final AssignmentJoinRepository joinRepository;
    private final StudentRepository studentRepository;
    private final QuestionRepository questionRepository;
    private final EligibilityService eligibility;
    private final RateLimiter rateLimiter;
    private final AppProperties props;

    public StudentAssignmentService(AssignmentRepository assignmentRepository, AttemptRepository attemptRepository,
                                    AssignmentJoinRepository joinRepository, StudentRepository studentRepository,
                                    QuestionRepository questionRepository, EligibilityService eligibility,
                                    RateLimiter rateLimiter, AppProperties props) {
        this.assignmentRepository = assignmentRepository;
        this.attemptRepository = attemptRepository;
        this.joinRepository = joinRepository;
        this.studentRepository = studentRepository;
        this.questionRepository = questionRepository;
        this.eligibility = eligibility;
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<StudentAssignmentView> list(Long studentId) {
        EligibilityService.StudentContext ctx = eligibility.context(studentId);
        Set<Long> seen = new HashSet<>();
        List<Assignment> visible = new ArrayList<>();
        for (Assignment a : assignmentRepository.findByStatus(AssignmentStatus.ACTIVE)) {
            if (eligibility.canView(a, ctx) && seen.add(a.getId())) visible.add(a);
        }
        // Closed assignments stay visible to students who attempted them (for results).
        for (Long id : ctx.attemptedAssignmentIds()) {
            if (seen.add(id)) assignmentRepository.findDetailedById(id).ifPresent(visible::add);
        }
        Instant now = Instant.now();
        Map<Long, List<Attempt>> attempts = attemptRepository.findByStudentId(studentId).stream()
                .collect(Collectors.groupingBy(x -> x.getAssignment().getId()));
        return visible.stream()
                .map(a -> view(a, attempts.getOrDefault(a.getId(), List.of()), now))
                .sorted(Comparator.comparing((StudentAssignmentView v) -> order(v.status()))
                        .thenComparing(v -> v.deadline() == null ? Instant.MAX : v.deadline()))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentAssignmentView get(Long assignmentId, Long studentId) {
        Assignment a = assignmentRepository.findDetailedById(assignmentId)
                .orElseThrow(() -> ApiException.notFound("Assignment"));
        if (!eligibility.canView(a, eligibility.context(studentId))) {
            throw ApiException.notFound("Assignment");
        }
        return view(a, attemptRepository.findByAssignmentIdAndStudentIdOrderByAttemptNumberAsc(assignmentId, studentId),
                Instant.now());
    }

    @Transactional
    public StudentAssignmentView join(String rawCode, Long studentId) {
        rateLimiter.check("join:" + studentId, props.rateLimit().joinPerMinute());
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        Assignment a = assignmentRepository.findByJoinCode(code)
                .filter(x -> x.getType() == AssignmentType.CODE)
                .orElseThrow(() -> ApiException.notFound("No quiz matches this join code"));
        a = assignmentRepository.findDetailedById(a.getId()).orElseThrow();
        if (a.getStatus() == AssignmentStatus.CLOSED) {
            throw ApiException.conflict("This quiz has been closed");
        }
        EligibilityService.StudentContext ctx = eligibility.context(studentId);
        if (!eligibility.canJoinWithCode(a, ctx)) {
            throw ApiException.forbidden("You are not eligible for this quiz. The code only works for its assigned students.");
        }
        if (!ctx.joinedAssignmentIds().contains(a.getId())) {
            AssignmentJoin j = new AssignmentJoin();
            j.setAssignment(a);
            j.setStudent(studentRepository.getReferenceById(studentId));
            joinRepository.saveAndFlush(j);
        }
        return view(a, attemptRepository.findByAssignmentIdAndStudentIdOrderByAttemptNumberAsc(a.getId(), studentId),
                Instant.now());
    }

    private StudentAssignmentView view(Assignment a, List<Attempt> attempts, Instant now) {
        Duration grace = Duration.ofSeconds(props.quiz().graceSeconds());
        Optional<Attempt> running = attempts.stream()
                .filter(x -> x.getStatus() == AttemptStatus.IN_PROGRESS && now.isBefore(x.getEndsAt().plus(grace)))
                .findFirst();
        Optional<Attempt> latestFinal = attempts.stream().filter(x -> x.getStatus().isFinal())
                .max(Comparator.comparing(Attempt::getAttemptNumber));
        // An expired but not yet finalized attempt counts as used and will be auto-submitted.
        boolean expiredRunning = attempts.stream()
                .anyMatch(x -> x.getStatus() == AttemptStatus.IN_PROGRESS && !now.isBefore(x.getEndsAt().plus(grace)));
        boolean visible = AssignmentRules.resultsVisible(a, now);
        String blocked = AssignmentRules.startBlockReason(a, now);
        boolean attemptsLeft = attempts.size() < a.getMaxAttempts();
        if (blocked == null && !attemptsLeft) blocked = "No attempts remaining";

        DashboardStatus status;
        if (running.isPresent()) status = DashboardStatus.IN_PROGRESS;
        else if (latestFinal.isPresent() || expiredRunning) {
            if (visible && latestFinal.isPresent()) status = DashboardStatus.RESULTS_RELEASED;
            else if (expiredRunning) status = DashboardStatus.AUTO_SUBMITTED;
            else status = latestFinal.get().getStatus() == AttemptStatus.AUTO_SUBMITTED
                    ? DashboardStatus.AUTO_SUBMITTED : DashboardStatus.SUBMITTED;
        } else if (AssignmentRules.isUpcoming(a, now)) status = DashboardStatus.UPCOMING;
        else if (blocked != null) status = DashboardStatus.EXPIRED;
        else status = DashboardStatus.AVAILABLE;

        List<Question> questions = questionRepository.findByQuizIdWithOptions(a.getQuiz().getId());
        Quiz q = a.getQuiz();
        return new StudentAssignmentView(a.getId(), a.getName(), q.getTitle(), q.getDescription(), q.getInstructions(),
                CourseRef.of(q.getCourse()), a.getType(), a.getAvailableFrom(), a.getDeadline(), a.getDurationMinutes(),
                a.getMaxAttempts(), attempts.size(), status, running.isEmpty() && blocked == null,
                running.isPresent() ? null : blocked, a.isLiveSession(), a.getSessionState(),
                running.map(Attempt::getId).orElse(null), latestFinal.map(Attempt::getId).orElse(null), visible,
                questions.size(), questions.stream().map(Question::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add),
                StudentProctoring.of(a), a.isLeaderboardEnabled(), now);
    }

    private static int order(DashboardStatus s) {
        return switch (s) {
            case IN_PROGRESS -> 0;
            case AVAILABLE -> 1;
            case UPCOMING -> 2;
            case RESULTS_RELEASED -> 3;
            case SUBMITTED, AUTO_SUBMITTED -> 4;
            case EXPIRED -> 5;
        };
    }
}
