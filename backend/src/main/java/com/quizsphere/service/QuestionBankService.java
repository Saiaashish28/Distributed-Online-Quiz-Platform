package com.quizsphere.service;

import com.quizsphere.dto.QuizDtos.*;
import com.quizsphere.dto.StudentDtos.CourseRef;
import com.quizsphere.entity.Question;
import com.quizsphere.entity.QuestionBank;
import com.quizsphere.entity.QuestionOption;
import com.quizsphere.entity.Quiz;
import com.quizsphere.exception.ApiException;
import com.quizsphere.repository.CourseRepository;
import com.quizsphere.repository.QuestionBankRepository;
import com.quizsphere.repository.QuestionRepository;
import com.quizsphere.repository.UserRepository;
import com.quizsphere.security.AuthUser;
import com.quizsphere.util.Text;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuestionBankService {

    private final QuestionBankRepository bankRepository;
    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final QuizService quizService;
    private final QuestionImportService importService;

    public QuestionBankService(QuestionBankRepository bankRepository, QuestionRepository questionRepository,
                               CourseRepository courseRepository, UserRepository userRepository,
                               QuizService quizService, QuestionImportService importService) {
        this.bankRepository = bankRepository;
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.quizService = quizService;
        this.importService = importService;
    }

    @Transactional(readOnly = true)
    public List<BankSummary> list(AuthUser user) {
        Map<Long, Long> counts = bankRepository.countQuestionsByBank(user.userId()).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        return bankRepository.findByOwnerIdOrderByNameAsc(user.userId()).stream()
                .map(b -> summary(b, counts.getOrDefault(b.getId(), 0L))).toList();
    }

    @Transactional
    public BankSummary create(BankRequest req, AuthUser user) {
        QuestionBank b = new QuestionBank();
        b.setOwner(userRepository.getReferenceById(user.userId()));
        apply(b, req);
        return summary(bankRepository.saveAndFlush(b), 0);
    }

    @Transactional
    public BankSummary update(Long id, BankRequest req, AuthUser user) {
        QuestionBank b = findOwned(id, user);
        apply(b, req);
        return summary(b, b.getQuestions().size());
    }

    @Transactional
    public void delete(Long id, AuthUser user) {
        bankRepository.delete(findOwned(id, user));
    }

    @Transactional(readOnly = true)
    public BankDetail detail(Long id, String search, AuthUser user) {
        QuestionBank b = findOwned(id, user);
        List<Question> questions = questionRepository.findByBankIdWithOptions(id);
        String term = Text.trimToNull(search);
        List<QuestionAdmin> filtered = questions.stream()
                .filter(q -> term == null || q.getText().toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)))
                .map(QuestionAdmin::of).toList();
        return new BankDetail(summary(b, questions.size()), filtered);
    }

    @Transactional
    public QuestionAdmin addQuestion(Long bankId, QuestionRequest req, AuthUser user) {
        QuestionBank b = findOwned(bankId, user);
        Question q = new Question();
        q.setBank(b);
        q.setPosition(questionRepository.maxPositionInBank(bankId) + 1);
        QuizService.apply(q, req);
        questionRepository.saveAndFlush(q);
        return QuestionAdmin.of(q);
    }

    @Transactional
    public QuestionImportResult importQuestions(Long bankId, MultipartFile file, boolean commit, boolean skipInvalid,
                                                AuthUser user) {
        QuestionBank b = findOwned(bankId, user);
        return quizService.commitImport(importService.parse(file), commit, skipInvalid,
                questionRepository.maxPositionInBank(bankId) + 1, q -> q.setBank(b));
    }

    /**
     * Copies bank questions into a draft quiz. The quiz receives independent copies, so later
     * bank edits never alter a quiz (and never a published one).
     */
    @Transactional
    public CopyResult copyToQuiz(Long bankId, CopyToQuizRequest req, AuthUser user) {
        findOwned(bankId, user);
        Quiz quiz = quizService.findEditable(req.quizId(), user);
        List<Question> source = questionRepository.findAllWithOptionsByIdIn(new LinkedHashSet<>(req.questionIds()));
        if (source.size() != new HashSet<>(req.questionIds()).size()
                || source.stream().anyMatch(q -> q.getBank() == null || !q.getBank().getId().equals(bankId))) {
            throw ApiException.badRequest("All selected questions must belong to this bank");
        }
        Map<Long, Question> byId = source.stream().collect(Collectors.toMap(Question::getId, q -> q));
        int pos = questionRepository.maxPositionInQuiz(quiz.getId()) + 1;
        int copied = 0;
        for (Long id : new LinkedHashSet<>(req.questionIds())) {
            Question src = byId.get(id);
            Question copy = new Question();
            copy.setQuiz(quiz);
            copy.setText(src.getText());
            copy.setPoints(src.getPoints());
            copy.setExplanation(src.getExplanation());
            copy.setSourceQuestionId(src.getId());
            copy.setPosition(pos++);
            for (QuestionOption o : src.getOptions()) {
                copy.addOption(o.getText(), o.isCorrect());
            }
            questionRepository.save(copy);
            copied++;
        }
        questionRepository.flush();
        return new CopyResult(copied);
    }

    private void apply(QuestionBank b, BankRequest req) {
        b.setName(req.name().trim());
        b.setDescription(Text.trimToNull(req.description()));
        b.setCourse(req.courseId() == null ? null : courseRepository.findById(req.courseId())
                .orElseThrow(() -> ApiException.badRequest("Course does not exist")));
    }

    private QuestionBank findOwned(Long id, AuthUser user) {
        QuestionBank b = bankRepository.findById(id).orElseThrow(() -> ApiException.notFound("Question bank"));
        if (!b.getOwner().getId().equals(user.userId())) {
            throw ApiException.notFound("Question bank");
        }
        return b;
    }

    private static BankSummary summary(QuestionBank b, long count) {
        return new BankSummary(b.getId(), b.getName(), b.getDescription(), CourseRef.of(b.getCourse()), count,
                b.getCreatedAt());
    }
}
