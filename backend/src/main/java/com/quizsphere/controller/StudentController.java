package com.quizsphere.controller;

import com.quizsphere.dto.StudentQuizDtos.*;
import com.quizsphere.security.CurrentUser;
import com.quizsphere.service.AttemptService;
import com.quizsphere.service.ProctoringService;
import com.quizsphere.service.StudentAssignmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Student endpoints. The student identity always comes from the token, never from the request. */
@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentAssignmentService assignmentService;
    private final AttemptService attemptService;
    private final ProctoringService proctoringService;

    public StudentController(StudentAssignmentService assignmentService, AttemptService attemptService,
                             ProctoringService proctoringService) {
        this.assignmentService = assignmentService;
        this.attemptService = attemptService;
        this.proctoringService = proctoringService;
    }

    @GetMapping("/assignments")
    public List<StudentAssignmentView> assignments() {
        return assignmentService.list(CurrentUser.studentId());
    }

    @PostMapping("/assignments/join")
    public StudentAssignmentView join(@Valid @RequestBody JoinRequest req) {
        return assignmentService.join(req.code(), CurrentUser.studentId());
    }

    @GetMapping("/assignments/{id}")
    public StudentAssignmentView assignment(@PathVariable Long id) {
        return assignmentService.get(id, CurrentUser.studentId());
    }

    @PostMapping("/assignments/{id}/start")
    public AttemptView start(@PathVariable Long id) {
        return attemptService.start(id, CurrentUser.studentId());
    }

    @GetMapping("/assignments/{id}/leaderboard")
    public List<LeaderboardEntry> leaderboard(@PathVariable Long id) {
        return attemptService.leaderboard(id, CurrentUser.studentId());
    }

    @GetMapping("/attempts/{id}")
    public AttemptView attempt(@PathVariable Long id) {
        return attemptService.get(id, CurrentUser.studentId());
    }

    @PutMapping("/attempts/{id}/answers")
    public SaveAnswersResponse saveAnswers(@PathVariable Long id, @Valid @RequestBody SaveAnswersRequest req) {
        return attemptService.saveAnswers(id, CurrentUser.studentId(), req);
    }

    @PostMapping("/attempts/{id}/submit")
    public SubmitResponse submit(@PathVariable Long id) {
        return attemptService.submit(id, CurrentUser.studentId());
    }

    @GetMapping("/attempts/{id}/result")
    public ResultView result(@PathVariable Long id) {
        return attemptService.result(id, CurrentUser.studentId());
    }

    @PostMapping("/attempts/{id}/proctoring-events")
    public ProctoringEventResponse proctoringEvent(@PathVariable Long id, @Valid @RequestBody ProctoringEventRequest req) {
        return proctoringService.record(id, CurrentUser.studentId(), req);
    }
}
