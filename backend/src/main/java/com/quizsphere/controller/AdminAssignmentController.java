package com.quizsphere.controller;

import com.quizsphere.dto.AssignmentDtos.*;
import com.quizsphere.dto.ProctoringDtos.*;
import com.quizsphere.security.CurrentUser;
import com.quizsphere.service.AssignmentService;
import com.quizsphere.service.DashboardService;
import com.quizsphere.service.ExportService;
import com.quizsphere.service.ExportService.ExportFile;
import com.quizsphere.service.ExportService.NonSubmitterMarks;
import com.quizsphere.service.ProctoringService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Assignments, live sessions, results, proctoring review and Excel exports. */
@RestController
@RequestMapping("/api/admin")
public class AdminAssignmentController {

    static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AssignmentService assignmentService;
    private final ProctoringService proctoringService;
    private final ExportService exportService;
    private final DashboardService dashboardService;

    public AdminAssignmentController(AssignmentService assignmentService, ProctoringService proctoringService,
                                     ExportService exportService, DashboardService dashboardService) {
        this.assignmentService = assignmentService;
        this.proctoringService = proctoringService;
        this.exportService = exportService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public DashboardService.AdminDashboard dashboard() {
        return dashboardService.get(CurrentUser.get());
    }

    @GetMapping("/assignments")
    public List<AssignmentSummary> assignments() {
        return assignmentService.list(CurrentUser.get());
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentDetail create(@Valid @RequestBody AssignmentRequest req) {
        return assignmentService.create(req, CurrentUser.get());
    }

    @GetMapping("/assignments/{id}")
    public AssignmentDetail get(@PathVariable Long id) {
        return assignmentService.detail(id, CurrentUser.get());
    }

    @PatchMapping("/assignments/{id}")
    public AssignmentDetail update(@PathVariable Long id, @Valid @RequestBody AssignmentUpdateRequest req) {
        return assignmentService.update(id, req, CurrentUser.get());
    }

    @DeleteMapping("/assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        assignmentService.delete(id, CurrentUser.get());
    }

    @PostMapping("/assignments/{id}/regenerate-code")
    public AssignmentDetail regenerateCode(@PathVariable Long id) {
        return assignmentService.regenerateCode(id, CurrentUser.get());
    }

    @PostMapping("/assignments/{id}/session/start")
    public AssignmentDetail startSession(@PathVariable Long id) {
        return assignmentService.startSession(id, CurrentUser.get());
    }

    @PostMapping("/assignments/{id}/session/end")
    public AssignmentDetail endSession(@PathVariable Long id) {
        return assignmentService.endSession(id, CurrentUser.get());
    }

    @PostMapping("/assignments/{id}/release-results")
    public AssignmentDetail releaseResults(@PathVariable Long id) {
        return assignmentService.releaseResults(id, CurrentUser.get());
    }

    @GetMapping("/assignments/{id}/results")
    public ResultsResponse results(@PathVariable Long id) {
        return assignmentService.results(id, CurrentUser.get());
    }

    @GetMapping("/attempts/{id}")
    public AdminAttemptDetail attempt(@PathVariable Long id) {
        return assignmentService.attemptDetail(id, CurrentUser.get());
    }

    // -------------------------------------------------------------------- proctoring

    @GetMapping("/assignments/{id}/proctoring-events")
    public AssignmentEvents proctoringEvents(@PathVariable Long id) {
        return proctoringService.forAssignment(id, CurrentUser.get());
    }

    @PatchMapping("/proctoring-events/{eventId}/review")
    public EventView review(@PathVariable Long eventId, @Valid @RequestBody ReviewRequest req) {
        return proctoringService.review(eventId, req, CurrentUser.get());
    }

    @PatchMapping("/attempts/{attemptId}/proctoring-review")
    public Map<String, Integer> reviewAttempt(@PathVariable Long attemptId, @Valid @RequestBody ReviewRequest req) {
        return Map.of("updated", proctoringService.reviewAttempt(attemptId, req, CurrentUser.get()));
    }

    // ----------------------------------------------------------------------- exports

    @GetMapping("/assignments/{id}/export/marksheet")
    public ResponseEntity<byte[]> marksheet(@PathVariable Long id,
                                            @RequestParam(required = false) Long groupId,
                                            @RequestParam(defaultValue = "BLANK") NonSubmitterMarks nonSubmitterMarks,
                                            @RequestParam(defaultValue = "true") boolean includeResponses,
                                            @RequestParam(required = false) String tz) {
        return file(exportService.assignmentMarksheet(id, groupId, nonSubmitterMarks, includeResponses,
                ExportService.zone(tz), CurrentUser.get()));
    }

    @GetMapping("/assignments/{id}/export/responses")
    public ResponseEntity<byte[]> responses(@PathVariable Long id, @RequestParam(required = false) String tz) {
        return file(exportService.assignmentResponses(id, ExportService.zone(tz), CurrentUser.get()));
    }

    @GetMapping("/quizzes/{id}/export/marksheet")
    public ResponseEntity<byte[]> quizMarksheet(@PathVariable Long id,
                                                @RequestParam(defaultValue = "BLANK") NonSubmitterMarks nonSubmitterMarks,
                                                @RequestParam(required = false) String tz) {
        return file(exportService.quizMarksheet(id, nonSubmitterMarks, ExportService.zone(tz), CurrentUser.get()));
    }

    @GetMapping("/exports/consolidated")
    public ResponseEntity<byte[]> consolidated(@RequestParam List<Long> assignmentIds,
                                               @RequestParam(required = false) Long groupId,
                                               @RequestParam(defaultValue = "BLANK") NonSubmitterMarks nonSubmitterMarks,
                                               @RequestParam(required = false) String tz) {
        return file(exportService.consolidated(assignmentIds, groupId, nonSubmitterMarks, ExportService.zone(tz),
                CurrentUser.get()));
    }

    static ResponseEntity<byte[]> file(ExportFile f) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        // Export filenames are sanitized to ASCII, so a plain filename parameter is enough.
                        ContentDisposition.attachment().filename(f.filename()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(f.content());
    }
}
