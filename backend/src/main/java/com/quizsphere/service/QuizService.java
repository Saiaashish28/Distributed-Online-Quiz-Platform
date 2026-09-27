package com.quizsphere.service;

import com.quizsphere.dto.QuizDtos.*;
import com.quizsphere.dto.StudentDtos.CourseRef;
import com.quizsphere.entity.*;
import com.quizsphere.exception.ApiException;
import com.quizsphere.repository.*;
import com.quizsphere.security.AuthUser;
import com.quizsphere.util.Text;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttemptRepository attemptRepository;
    private final QuestionImportService importService;

    public QuizService(QuizRepository quizRepository, QuestionRepository questionRepository,
                       CourseRepository courseRepository, UserRepository userRepository,
                       AssignmentRepository assignmentRepository, AttemptRepository attemptRepository,
                       QuestionImportService importService) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.attemptRepository = attemptRepository;
        this.importService = importService;
    }

    // ------------------------------------------------------------------------ quizzes

    @Transactional(readOnly = true)
    public List<QuizSummary> list(AuthUser user) {
        Map<Long, Object[]> stats = quizRepository.questionStatsByOwner(user.userId()).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> r));
        return quizRepository.findByOwnerIdOrderByUpdatedAtDesc(user.userId()).stream().map(q -> {
            Object[] s = stats.get(q.getId());
            return new QuizSummary(q.getId(), q.getTitle(), CourseRef.of(q.getCourse()), q.getDurationMinutes(),
                    q.getStatus(), s == null ? 0 : (Long) s[1], s == null ? BigDecimal.ZERO : (BigDecimal) s[2],
                    q.getUpdatedAt(), q.getPublishedAt());
        }).toList();
    }

    @Transactional
    public QuizDetail create(QuizRequest req, AuthUser user) {
        Quiz q = new Quiz();
        q.setTitle(req.title().trim());
        q.setDescription(Text.trimToNull(req.description()));
        q.setInstructions(Text.trimToNull(req.instructions()));
        q.setCourse(req.courseId() == null ? null : findCourse(req.courseId()));
        q.setDurationMinutes(req.durationMinutes());
        q.setShuffleQuestions(req.shuffleQuestions());
        q.setShuffleOptions(req.shuffleOptions());
        q.setOwner(userRepository.getReferenceById(user.userId()));
        quizRepository.saveAndFlush(q);
        return detail(q.getId(), user);
    }

    @Transactional(readOnly = true)
    public QuizDetail detail(Long id, AuthUser user) {
        Quiz q = findOwned(id, user);
        List<Question> questions = questionRepository.findByQuizIdWithOptions(id);
        BigDecimal total = questions.stream().map(Question::getPoints).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new QuizDetail(q.getId(), q.getTitle(), q.getDescription(), q.getInstructions(), CourseRef.of(q.getCourse()),
                q.getDurationMinutes(), q.getStatus(), q.isShuffleQuestions(), q.isShuffleOptions(),
                questions.stream().map(QuestionAdmin::of).toList(), total, q.getStatus() == QuizStatus.DRAFT,
                assignmentRepository.existsByQuizId(id), q.getCreatedAt(), q.getUpdatedAt(), q.getPublishedAt());
    }

    @Transactional
    public QuizDetail update(Long id, QuizUpdateRequest req, AuthUser user) {
        Quiz q = findOwned(id, user);
        if (q.getStatus() == QuizStatus.ARCHIVED) {
            throw ApiException.conflict("Archived quizzes cannot be edited");
        }
        if (req.title() != null) q.setTitle(req.title().trim());
        if (req.description() != null) q.setDescription(Text.trimToNull(req.description()));
        if (req.instructions() != null) q.setInstructions(Text.trimToNull(req.instructions()));
        if (Boolean.TRUE.equals(req.clearCourse())) q.setCourse(null);
        else if (req.courseId() != null) q.setCourse(findCourse(req.courseId()));
        if (req.durationMinutes() != null) q.setDurationMinutes(req.durationMinutes());
        if (req.shuffleQuestions() != null) q.setShuffleQuestions(req.shuffleQuestions());
        if (req.shuffleOptions() != null) q.setShuffleOptions(req.shuffleOptions());
        quizRepository.saveAndFlush(q);
        return detail(id, user);
    }

    @Transactional
    public void delete(Long id, AuthUser user) {
        Quiz q = findOwned(id, user);
        if (assignmentRepository.existsByQuizId(id)) {
            throw ApiException.conflict("This quiz has assignments. Archive it instead of deleting.");
        }
        quizRepository.delete(q);
    }

    @Transactional
    public QuizDetail publish(Long id, AuthUser user) {
        Quiz q = findOwned(id, user);
        if (q.getStatus() == QuizStatus.PUBLISHED) {
            return detail(id, user);
        }
        List<ValidationProblem> problems = validateForPublish(questionRepository.findByQuizIdWithOptions(id));
        if (!problems.isEmpty()) {
            throw ApiException.unprocessable("The quiz cannot be published until these problems are fixed", problems);
        }
        q.setStatus(QuizStatus.PUBLISHED);
        q.setPublishedAt(Instant.now());
        return detail(id, user);
    }

    /** Returns a published quiz to draft so it can be edited; only allowed before anyone attempts it. */
    @Transactional
    public QuizDetail unpublish(Long id, AuthUser user) {
        Quiz q = findOwned(id, user);
        if (attemptRepository.existsByAssignmentQuizId(id)) {
            throw ApiException.conflict("Students have already attempted this quiz, so it can no longer be edited");
        }
        q.setStatus(QuizStatus.DRAFT);
        return detail(id, user);
    }

    @Transactional
    public QuizDetail archive(Long id, AuthUser user) {
        Quiz q = findOwned(id, user);
        q.setStatus(QuizStatus.ARCHIVED);
        return detail(id, user);
    }

    public static List<ValidationProblem> validateForPublish(List<Question> questions) {
        List<ValidationProblem> problems = new ArrayList<>();
        if (questions.isEmpty()) {
            problems.add(new ValidationProblem(null, null, "Add at least one question"));
        }
        int n = 1;
        for (Question q : questions) {
            int pos = n++;
            if (q.getText() == null || q.getText().isBlank()) {
                problems.add(new ValidationProblem(q.getId(), pos, "Question text is empty"));
            }
            if (q.getPoints() == null || q.getPoints().signum() <= 0) {
                problems.add(new ValidationProblem(q.getId(), pos, "Marks must be greater than 0"));
            }
            if (q.getOptions().size() < 2 || q.getOptions().size() > 6) {
                problems.add(new ValidationProblem(q.getId(), pos, "Needs 2 to 6 options"));
            }
            long correct = q.getOptions().stream().filter(QuestionOption::isCorrect).count();
            if (correct != 1) {
                problems.add(new ValidationProblem(q.getId(), pos, "Needs exactly one correct option"));
            }
            Set<String> texts = new HashSet<>();
            for (QuestionOption o : q.getOptions()) {
                if (o.getText() == null || o.getText().isBlank()) {
                    problems.add(new ValidationProblem(q.getId(), pos, "An option is empty"));
                } else if (!texts.add(o.getText().trim().toLowerCase(Locale.ROOT))) {
                    problems.add(new ValidationProblem(q.getId(), pos, "Options must be distinct"));
                }
            }
        }
        return problems;
    }

    // ---------------------------------------------------------------------- questions

    @Transactional
    public QuestionAdmin addQuestion(Long quizId, QuestionRequest req, AuthUser user) {
        Quiz quiz = findEditable(quizId, user);
        Question q = new Question();
        q.setQuiz(quiz);
        q.setPosition(questionRepository.maxPositionInQuiz(quizId) + 1);
        apply(q, req);
        questionRepository.saveAndFlush(q);
        return QuestionAdmin.of(q);
    }

    @Transactional
    public QuestionAdmin updateQuestion(Long questionId, QuestionRequest req, AuthUser user) {
        Question q = findOwnedQuestion(questionId, user);
        apply(q, req);
        questionRepository.saveAndFlush(q);
        return QuestionAdmin.of(q);
    }

    @Transactional
    public void deleteQuestion(Long questionId, AuthUser user) {
        Question q = findOwnedQuestion(questionId, user);
        if (q.getQuiz() != null) q.getQuiz().getQuestions().remove(q);
        if (q.getBank() != null) q.getBank().getQuestions().remove(q);
        questionRepository.delete(q);
    }

    @Transactional
    public QuizDetail reorder(Long quizId, ReorderRequest req, AuthUser user) {
        findEditable(quizId, user);
        List<Question> questions = questionRepository.findByQuizIdWithOptions(quizId);
        Map<Long, Question> byId = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));
        if (req.questionIds().size() != questions.size() || !byId.keySet().equals(new HashSet<>(req.questionIds()))) {
            throw ApiException.badRequest("The new order must list every question of the quiz exactly once");
        }
        for (int i = 0; i < req.questionIds().size(); i++) {
            byId.get(req.questionIds().get(i)).setPosition(i);
        }
        questionRepository.flush();
        return detail(quizId, user);
    }

    @Transactional
    public QuestionImportResult importQuestions(Long quizId, MultipartFile file, boolean commit, boolean skipInvalid,
                                                AuthUser user) {
        Quiz quiz = findEditable(quizId, user);
        QuestionImportService.Parsed parsed = importService.parse(file);
        return commitImport(parsed, commit, skipInvalid, questionRepository.maxPositionInQuiz(quizId) + 1,
                q -> q.setQuiz(quiz));
    }

    /** Shared by quiz and bank imports: returns a preview, or saves valid rows after explicit confirmation. */
    QuestionImportResult commitImport(QuestionImportService.Parsed parsed, boolean commit, boolean skipInvalid,
                                      int startPosition, java.util.function.Consumer<Question> attach) {
        int valid = (int) parsed.rows().stream().filter(QuestionImportRow::valid).count();
        int invalid = parsed.rows().size() - valid;
        QuestionImportResult preview = new QuestionImportResult(parsed.fileName(), false, parsed.rows().size(),
                valid, invalid, 0, parsed.headerErrors(), parsed.rows());
        if (!commit) {
            return preview;
        }
        if (!parsed.headerErrors().isEmpty()) {
            throw ApiException.unprocessable("The file's columns do not match the template", preview);
        }
        if (invalid > 0 && !skipInvalid) {
            throw ApiException.unprocessable(invalid + " row(s) are invalid. Fix them or explicitly choose to skip invalid rows.", preview);
        }
        if (valid == 0) {
            throw ApiException.unprocessable("There are no valid questions to import", preview);
        }
        int pos = startPosition;
        for (QuestionImportRow row : parsed.rows()) {
            if (!row.valid()) continue;
            Question q = importService.toQuestion(row);
            q.setPosition(pos++);
            attach.accept(q);
            questionRepository.save(q);
        }
        questionRepository.flush();
        return new QuestionImportResult(parsed.fileName(), true, parsed.rows().size(), valid, invalid, valid,
                parsed.headerErrors(), parsed.rows());
    }

    // ------------------------------------------------------------------------ helpers

    static void apply(Question q, QuestionRequest req) {
        long correct = req.options().stream().filter(OptionRequest::correct).count();
        if (correct != 1) {
            throw ApiException.badRequest("Mark exactly one option as correct");
        }
        Set<String> texts = new HashSet<>();
        for (OptionRequest o : req.options()) {
            if (!texts.add(o.text().trim().toLowerCase(Locale.ROOT))) {
                throw ApiException.badRequest("Options must be distinct");
            }
        }
        q.setText(req.text().trim());
        q.setPoints(req.points());
        q.setExplanation(Text.trimToNull(req.explanation()));
        // Update options in place where possible so option ids stay stable.
        List<QuestionOption> existing = q.getOptions();
        for (int i = 0; i < req.options().size(); i++) {
            OptionRequest o = req.options().get(i);
            if (i < existing.size()) {
                existing.get(i).setText(o.text().trim());
                existing.get(i).setCorrect(o.correct());
                existing.get(i).setPosition(i);
            } else {
                q.addOption(o.text().trim(), o.correct());
            }
        }
        while (existing.size() > req.options().size()) {
            existing.remove(existing.size() - 1);
        }
    }

    Quiz findOwned(Long id, AuthUser user) {
        Quiz q = quizRepository.findById(id).orElseThrow(() -> ApiException.notFound("Quiz"));
        if (!q.getOwner().getId().equals(user.userId())) {
            throw ApiException.notFound("Quiz");
        }
        return q;
    }

    Quiz findEditable(Long id, AuthUser user) {
        Quiz q = findOwned(id, user);
        if (q.getStatus() != QuizStatus.DRAFT) {
            throw ApiException.conflict("Only draft quizzes can be edited. Move the quiz back to draft first.");
        }
        return q;
    }

    private Question findOwnedQuestion(Long id, AuthUser user) {
        Question q = questionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Question"));
        if (q.getQuiz() != null) {
            findEditable(q.getQuiz().getId(), user);
        } else if (!q.getBank().getOwner().getId().equals(user.userId())) {
            throw ApiException.notFound("Question");
        }
        return q;
    }

    private Course findCourse(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> ApiException.badRequest("Course does not exist"));
    }
}
