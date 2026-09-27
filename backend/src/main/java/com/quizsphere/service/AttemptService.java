package com.quizsphere.service;

import com.quizsphere.config.AppProperties;
import com.quizsphere.dto.AssignmentDtos.ReviewedOption;
import com.quizsphere.dto.AssignmentDtos.ReviewedQuestion;
import com.quizsphere.dto.StudentDtos.StudentRef;
import com.quizsphere.dto.StudentQuizDtos.*;
import com.quizsphere.entity.*;
import com.quizsphere.exception.ApiException;
import com.quizsphere.repository.*;
import com.quizsphere.websocket.RealtimeHub;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Attempt lifecycle. All timing decisions use the server clock; client timers are display-only.
 * Row locks (SELECT ... FOR UPDATE) serialize concurrent start/save/submit requests so that
 * retries cannot create duplicate attempts or change a finalized score.
 */
@Service
public class AttemptService {

    private final AttemptRepository attemptRepository;
    private final AnswerRepository answerRepository;
    private final AssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final QuestionRepository questionRepository;
    private final EligibilityService eligibility;
    private final ProgressService progressService;
    private final RealtimeHub hub;
    private final Duration grace;

    @PersistenceContext
    private EntityManager entityManager;

    public AttemptService(AttemptRepository attemptRepository, AnswerRepository answerRepository,
                          AssignmentRepository assignmentRepository, StudentRepository studentRepository,
                          QuestionRepository questionRepository, EligibilityService eligibility,
                          ProgressService progressService, RealtimeHub hub, AppProperties props) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.assignmentRepository = assignmentRepository;
        this.studentRepository = studentRepository;
        this.questionRepository = questionRepository;
        this.eligibility = eligibility;
        this.progressService = progressService;
        this.hub = hub;
        this.grace = Duration.ofSeconds(props.quiz().graceSeconds());
    }

    // ------------------------------------------------------------------------- start

    @Transactional
    public AttemptView start(Long assignmentId, Long studentId) {
        Student student = studentRepository.lockById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
        Assignment a = assignmentRepository.findDetailedById(assignmentId)
                .orElseThrow(() -> ApiException.notFound("Assignment"));
        EligibilityService.StudentContext ctx = eligibility.context(studentId);
        if (!student.isActive() || !eligibility.canView(a, ctx)) {
            throw ApiException.notFound("Assignment");
        }
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);

        Optional<Attempt> running = attemptRepository.findByAssignmentIdAndStudentIdAndStatus(
                assignmentId, studentId, AttemptStatus.IN_PROGRESS);
        if (running.isPresent()) {
            if (now.isBefore(running.get().getEndsAt().plus(grace))) {
                return view(running.get(), now); // resume
            }
            Attempt expired = running.get();
            entityManager.refresh(expired, LockModeType.PESSIMISTIC_WRITE);
            if (expired.getStatus() == AttemptStatus.IN_PROGRESS) {
                finalizeAttempt(expired, AttemptStatus.AUTO_SUBMITTED, now);
            }
        }
        if (!eligibility.isTargeted(a, ctx) && !(a.getType() == AssignmentType.CODE && eligibility.canJoinWithCode(a, ctx))) {
            throw ApiException.forbidden("You are no longer eligible to start this quiz");
        }
        String blocked = AssignmentRules.startBlockReason(a, now);
        if (blocked != null) {
            throw ApiException.conflict(blocked);
        }
        int used = attemptRepository.findByAssignmentIdAndStudentIdOrderByAttemptNumberAsc(assignmentId, studentId).size();
        if (used >= a.getMaxAttempts()) {
            throw ApiException.conflict("You have used all " + a.getMaxAttempts() + " attempt(s) for this quiz");
        }
        List<Question> questions = questionRepository.findByQuizIdWithOptions(a.getQuiz().getId());
        if (questions.isEmpty()) {
            throw ApiException.conflict("This quiz has no questions");
        }
        Attempt at = new Attempt();
        at.setAssignment(a);
        at.setStudent(student);
        at.setAttemptNumber(used + 1);
        at.setStartedAt(now);
        at.setEndsAt(AssignmentRules.attemptEndsAt(a, now));
        at.setStatus(AttemptStatus.IN_PROGRESS);
        at.setQuestionCount(questions.size());
        at.setMaximumScore(questions.stream().map(Question::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add));
        attemptRepository.saveAndFlush(at);
        progressService.publish(assignmentId);
        return view(at, now);
    }

    // -------------------------------------------------------------------------- view

    @Transactional
    public AttemptView get(Long attemptId, Long studentId) {
        Attempt at = ownAttempt(attemptId, studentId);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (at.getStatus() == AttemptStatus.IN_PROGRESS && !now.isBefore(at.getEndsAt().plus(grace))) {
            entityManager.refresh(at, LockModeType.PESSIMISTIC_WRITE);
            if (at.getStatus() == AttemptStatus.IN_PROGRESS) {
                finalizeAttempt(at, AttemptStatus.AUTO_SUBMITTED, now);
            }
        }
        return view(at, now);
    }

    private AttemptView view(Attempt at, Instant now) {
        Assignment a = at.getAssignment();
        Quiz quiz = a.getQuiz();
        List<Question> questions = new ArrayList<>(questionRepository.findByQuizIdWithOptions(quiz.getId()));
        if (quiz.isShuffleQuestions()) {
            Collections.shuffle(questions, new Random(at.getId()));
        }
        List<StudentQuestion> qs = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            List<QuestionOption> opts = new ArrayList<>(q.getOptions());
            if (quiz.isShuffleOptions()) {
                Collections.shuffle(opts, new Random(at.getId() * 31 + q.getId()));
            }
            // Correct flags are never included in the student payload.
            qs.add(new StudentQuestion(q.getId(), i + 1, q.getText(), q.getPoints(),
                    opts.stream().map(o -> new StudentOption(o.getId(), o.getText())).toList()));
        }
        List<SavedAnswer> answers = answerRepository.findByAttemptId(at.getId()).stream()
                .map(x -> new SavedAnswer(x.getQuestionId(), x.getOptionId(), x.getClientSeq(), x.getUpdatedAt()))
                .toList();
        return new AttemptView(at.getId(), a.getId(), a.getName(), quiz.getTitle(), quiz.getInstructions(),
                StudentRef.of(at.getStudent()), at.getStatus(), at.getAttemptNumber(), at.getStartedAt(),
                at.getEndsAt(), now, qs, answers, StudentProctoring.of(a), at.getProctoringWarningCount());
    }

    // ------------------------------------------------------------------------ answers

    @Transactional
    public SaveAnswersResponse saveAnswers(Long attemptId, Long studentId, SaveAnswersRequest req) {
        Attempt at = lockOwnAttempt(attemptId, studentId);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (at.getStatus().isFinal()) {
            throw ApiException.conflict("This attempt has already been submitted; answers can no longer change");
        }
        if (!now.isBefore(at.getEndsAt().plus(grace))) {
            throw ApiException.conflict("Time is up. Your saved answers will be submitted automatically.");
        }
        Map<Long, Set<Long>> structure = questionRepository.findByQuizIdWithOptions(at.getAssignment().getQuiz().getId())
                .stream().collect(Collectors.toMap(Question::getId,
                        q -> q.getOptions().stream().map(QuestionOption::getId).collect(Collectors.toSet())));
        for (AnswerItem item : req.answers()) {
            Set<Long> options = structure.get(item.questionId());
            if (options == null) {
                throw ApiException.badRequest("Question " + item.questionId() + " is not part of this quiz");
            }
            if (item.optionId() != null && !options.contains(item.optionId())) {
                throw ApiException.badRequest("Option " + item.optionId() + " does not belong to question " + item.questionId());
            }
        }
        for (AnswerItem item : req.answers()) {
            answerRepository.upsert(attemptId, item.questionId(), item.optionId(), item.seq(), now);
        }
        List<SavedAnswer> saved = answerRepository.findByAttemptId(attemptId).stream()
                .map(x -> new SavedAnswer(x.getQuestionId(), x.getOptionId(), x.getClientSeq(), x.getUpdatedAt()))
                .toList();
        return new SaveAnswersResponse(now, saved, at.getEndsAt(), now);
    }

    // ------------------------------------------------------------------------- submit

    /** Idempotent: a retry after success returns the same finalized attempt unchanged. */
    @Transactional
    public SubmitResponse submit(Long attemptId, Long studentId) {
        Attempt at = lockOwnAttempt(attemptId, studentId);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (at.getStatus() == AttemptStatus.IN_PROGRESS) {
            AttemptStatus status = now.isBefore(at.getEndsAt().plus(grace))
                    ? AttemptStatus.SUBMITTED : AttemptStatus.AUTO_SUBMITTED;
            finalizeAttempt(at, status, now);
        }
        int answered = (int) answerRepository.findByAttemptId(attemptId).stream()
                .filter(x -> x.getOptionId() != null).count();
        return new SubmitResponse(at.getId(), at.getStatus(), at.getSubmittedAt(),
                AssignmentRules.resultsVisible(at.getAssignment(), now), answered, at.getQuestionCount());
    }

    /** Finalizes an attempt by id if it is still running (used by the scheduler and session end). */
    @Transactional
    public boolean autoFinalize(Long attemptId) {
        Attempt at = attemptRepository.lockById(attemptId).orElse(null);
        if (at == null || at.getStatus().isFinal()) return false;
        finalizeAttempt(at, AttemptStatus.AUTO_SUBMITTED, Instant.now());
        return true;
    }

    /**
     * Scores the attempt from saved answers on the server. Unanswered questions score zero.
     * Caller must hold the attempt row lock.
     */
    void finalizeAttempt(Attempt at, AttemptStatus status, Instant now) {
        finalizeAttempt(at, status, now, null);
    }

    /** @param reason optional cause sent to the student client, e.g. "PROCTORING". */
    void finalizeAttempt(Attempt at, AttemptStatus status, Instant now, String reason) {
        List<Question> questions = questionRepository.findByQuizIdWithOptions(at.getAssignment().getQuiz().getId());
        Map<Long, Answer> answers = answerRepository.findByAttemptId(at.getId()).stream()
                .collect(Collectors.toMap(Answer::getQuestionId, x -> x));
        BigDecimal score = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        int correct = 0;
        for (Question q : questions) {
            max = max.add(q.getPoints());
            Answer ans = answers.get(q.getId());
            if (ans == null) continue;
            Long correctId = q.correctOption().map(QuestionOption::getId).orElse(null);
            boolean ok = ans.getOptionId() != null && ans.getOptionId().equals(correctId);
            ans.setCorrect(ans.getOptionId() == null ? null : ok);
            ans.setMarksAwarded(ok ? q.getPoints() : BigDecimal.ZERO);
            if (ok) {
                score = score.add(q.getPoints());
                correct++;
            }
        }
        at.setStatus(status);
        at.setScore(score);
        at.setMaximumScore(max);
        at.setCorrectCount(correct);
        at.setQuestionCount(questions.size());
        at.setSubmittedAt(status == AttemptStatus.AUTO_SUBMITTED && at.getEndsAt().isBefore(now) ? at.getEndsAt() : now);
        attemptRepository.saveAndFlush(at);

        Long assignmentId = at.getAssignment().getId();
        hub.toStudent(assignmentId, at.getStudent().getId(), "attempt_finalized",
                reason == null ? Map.of("attemptId", at.getId(), "status", status)
                        : Map.of("attemptId", at.getId(), "status", status, "reason", reason));
        progressService.publish(assignmentId);
    }

    // ------------------------------------------------------------------------ results

    @Transactional(readOnly = true)
    public ResultView result(Long attemptId, Long studentId) {
        Attempt at = ownAttempt(attemptId, studentId);
        if (!at.getStatus().isFinal()) {
            throw ApiException.conflict("This attempt has not been submitted yet");
        }
        Assignment a = at.getAssignment();
        boolean released = AssignmentRules.resultsVisible(a, Instant.now());
        if (!released) {
            // Score, correct answers and explanations stay hidden until release.
            return new ResultView(at.getId(), a.getId(), a.getName(), a.getQuiz().getTitle(), at.getStatus(),
                    at.getAttemptNumber(), at.getSubmittedAt(), false, null, null, null, at.getQuestionCount(),
                    null, a.isLeaderboardEnabled(), List.of());
        }
        BigDecimal pct = at.getMaximumScore().signum() == 0 ? BigDecimal.ZERO
                : at.getScore().multiply(BigDecimal.valueOf(100)).divide(at.getMaximumScore(), 1, RoundingMode.HALF_UP);
        return new ResultView(at.getId(), a.getId(), a.getName(), a.getQuiz().getTitle(), at.getStatus(),
                at.getAttemptNumber(), at.getSubmittedAt(), true, at.getScore(), at.getMaximumScore(),
                at.getCorrectCount(), at.getQuestionCount(), pct, a.isLeaderboardEnabled(), review(at));
    }

    /** Per-question review with correct answers (admin, or student after release). */
    @Transactional(readOnly = true)
    public List<ReviewedQuestion> review(Attempt at) {
        List<Question> questions = questionRepository.findByQuizIdWithOptions(at.getAssignment().getQuiz().getId());
        Map<Long, Answer> answers = answerRepository.findByAttemptId(at.getId()).stream()
                .collect(Collectors.toMap(Answer::getQuestionId, x -> x));
        List<ReviewedQuestion> out = new ArrayList<>();
        int n = 1;
        for (Question q : questions) {
            Answer ans = answers.get(q.getId());
            out.add(new ReviewedQuestion(q.getId(), n++, q.getText(), q.getPoints(),
                    q.getOptions().stream().map(o -> new ReviewedOption(o.getId(), o.getText(), o.isCorrect())).toList(),
                    ans == null ? null : ans.getOptionId(), ans == null ? null : ans.getCorrect(),
                    ans == null || ans.getMarksAwarded() == null ? BigDecimal.ZERO : ans.getMarksAwarded(),
                    q.getExplanation(), ans == null ? null : ans.getUpdatedAt()));
        }
        return out;
    }

    /** Leaderboard: best attempt per student; score desc, submission time asc, attempt id asc. */
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard(Long assignmentId, Long studentId) {
        Assignment a = assignmentRepository.findDetailedById(assignmentId)
                .orElseThrow(() -> ApiException.notFound("Assignment"));
        if (!eligibility.canView(a, eligibility.context(studentId))) {
            throw ApiException.notFound("Assignment");
        }
        if (!a.isLeaderboardEnabled() || !AssignmentRules.resultsVisible(a, Instant.now())) {
            throw ApiException.forbidden("The leaderboard is not available for this quiz");
        }
        Map<Long, List<Attempt>> byStudent = attemptRepository.findByAssignmentIdOrderByIdAsc(assignmentId).stream()
                .collect(Collectors.groupingBy(x -> x.getStudent().getId()));
        List<Attempt> best = byStudent.values().stream()
                .map(AssignmentRules::bestFinal).flatMap(Optional::stream)
                .sorted(AssignmentRules.BEST_ATTEMPT).toList();
        List<LeaderboardEntry> out = new ArrayList<>();
        for (int i = 0; i < best.size(); i++) {
            Attempt x = best.get(i);
            boolean me = x.getStudent().getId().equals(studentId);
            if (i < 50 || me) {
                out.add(new LeaderboardEntry(i + 1, x.getStudent().getFullName(), x.getScore(), x.getMaximumScore(),
                        x.getSubmittedAt(), me));
            }
        }
        return out;
    }

    /** Locks the attempt row before reading its state, so the status check cannot be stale. */
    private Attempt lockOwnAttempt(Long attemptId, Long studentId) {
        Attempt at = attemptRepository.lockById(attemptId).orElseThrow(() -> ApiException.notFound("Attempt"));
        if (!at.getStudent().getId().equals(studentId)) {
            throw ApiException.notFound("Attempt");
        }
        return at;
    }

    private Attempt ownAttempt(Long attemptId, Long studentId) {
        Attempt at = attemptRepository.findById(attemptId).orElseThrow(() -> ApiException.notFound("Attempt"));
        if (!at.getStudent().getId().equals(studentId)) {
            throw ApiException.notFound("Attempt"); // do not reveal other students' attempts
        }
        return at;
    }
}
