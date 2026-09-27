package com.quizsphere.controller;

import com.quizsphere.dto.QuizDtos.*;
import com.quizsphere.security.CurrentUser;
import com.quizsphere.service.QuestionBankService;
import com.quizsphere.service.QuizService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Quizzes, questions and question banks. */
@RestController
@RequestMapping("/api/admin")
public class AdminQuizController {

    private final QuizService quizService;
    private final QuestionBankService bankService;

    public AdminQuizController(QuizService quizService, QuestionBankService bankService) {
        this.quizService = quizService;
        this.bankService = bankService;
    }

    // ----------------------------------------------------------------------- quizzes

    @GetMapping("/quizzes")
    public List<QuizSummary> quizzes() {
        return quizService.list(CurrentUser.get());
    }

    @PostMapping("/quizzes")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDetail createQuiz(@Valid @RequestBody QuizRequest req) {
        return quizService.create(req, CurrentUser.get());
    }

    @GetMapping("/quizzes/{id}")
    public QuizDetail quiz(@PathVariable Long id) {
        return quizService.detail(id, CurrentUser.get());
    }

    @PatchMapping("/quizzes/{id}")
    public QuizDetail updateQuiz(@PathVariable Long id, @Valid @RequestBody QuizUpdateRequest req) {
        return quizService.update(id, req, CurrentUser.get());
    }

    @DeleteMapping("/quizzes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuiz(@PathVariable Long id) {
        quizService.delete(id, CurrentUser.get());
    }

    @PostMapping("/quizzes/{id}/publish")
    public QuizDetail publish(@PathVariable Long id) {
        return quizService.publish(id, CurrentUser.get());
    }

    @PostMapping("/quizzes/{id}/unpublish")
    public QuizDetail unpublish(@PathVariable Long id) {
        return quizService.unpublish(id, CurrentUser.get());
    }

    @PostMapping("/quizzes/{id}/archive")
    public QuizDetail archive(@PathVariable Long id) {
        return quizService.archive(id, CurrentUser.get());
    }

    @PostMapping("/quizzes/{id}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionAdmin addQuestion(@PathVariable Long id, @Valid @RequestBody QuestionRequest req) {
        return quizService.addQuestion(id, req, CurrentUser.get());
    }

    @PostMapping("/quizzes/{id}/questions/reorder")
    public QuizDetail reorder(@PathVariable Long id, @Valid @RequestBody ReorderRequest req) {
        return quizService.reorder(id, req, CurrentUser.get());
    }

    /** mode=preview (default) validates only; mode=commit imports after explicit confirmation. */
    @PostMapping(value = "/quizzes/{id}/questions/import", consumes = "multipart/form-data")
    public QuestionImportResult importToQuiz(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                             @RequestParam(defaultValue = "preview") String mode,
                                             @RequestParam(defaultValue = "false") boolean skipInvalid) {
        return quizService.importQuestions(id, file, "commit".equalsIgnoreCase(mode), skipInvalid, CurrentUser.get());
    }

    @PatchMapping("/questions/{id}")
    public QuestionAdmin updateQuestion(@PathVariable Long id, @Valid @RequestBody QuestionRequest req) {
        return quizService.updateQuestion(id, req, CurrentUser.get());
    }

    @DeleteMapping("/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuestion(@PathVariable Long id) {
        quizService.deleteQuestion(id, CurrentUser.get());
    }

    // ---------------------------------------------------------------- question banks

    @GetMapping("/question-banks")
    public List<BankSummary> banks() {
        return bankService.list(CurrentUser.get());
    }

    @PostMapping("/question-banks")
    @ResponseStatus(HttpStatus.CREATED)
    public BankSummary createBank(@Valid @RequestBody BankRequest req) {
        return bankService.create(req, CurrentUser.get());
    }

    @GetMapping("/question-banks/{id}")
    public BankDetail bank(@PathVariable Long id, @RequestParam(required = false) String q) {
        return bankService.detail(id, q, CurrentUser.get());
    }

    @PatchMapping("/question-banks/{id}")
    public BankSummary updateBank(@PathVariable Long id, @Valid @RequestBody BankRequest req) {
        return bankService.update(id, req, CurrentUser.get());
    }

    @DeleteMapping("/question-banks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBank(@PathVariable Long id) {
        bankService.delete(id, CurrentUser.get());
    }

    @PostMapping("/question-banks/{id}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionAdmin addBankQuestion(@PathVariable Long id, @Valid @RequestBody QuestionRequest req) {
        return bankService.addQuestion(id, req, CurrentUser.get());
    }

    @PostMapping(value = "/question-banks/{id}/questions/import", consumes = "multipart/form-data")
    public QuestionImportResult importToBank(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                             @RequestParam(defaultValue = "preview") String mode,
                                             @RequestParam(defaultValue = "false") boolean skipInvalid) {
        return bankService.importQuestions(id, file, "commit".equalsIgnoreCase(mode), skipInvalid, CurrentUser.get());
    }

    @PostMapping("/question-banks/{id}/copy-to-quiz")
    public CopyResult copyToQuiz(@PathVariable Long id, @Valid @RequestBody CopyToQuizRequest req) {
        return bankService.copyToQuiz(id, req, CurrentUser.get());
    }
}
