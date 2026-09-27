package com.quizsphere.controller;

import com.quizsphere.dto.CourseGroupDtos.*;
import com.quizsphere.dto.PageResponse;
import com.quizsphere.dto.StudentDtos.*;
import com.quizsphere.security.CurrentUser;
import com.quizsphere.service.CourseService;
import com.quizsphere.service.GroupService;
import com.quizsphere.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Students, courses and groups. */
@RestController
@RequestMapping("/api/admin")
public class AdminPeopleController {

    private final StudentService studentService;
    private final CourseService courseService;
    private final GroupService groupService;

    public AdminPeopleController(StudentService studentService, CourseService courseService, GroupService groupService) {
        this.studentService = studentService;
        this.courseService = courseService;
        this.groupService = groupService;
    }

    // ---------------------------------------------------------------------- students

    @GetMapping("/students")
    public PageResponse<StudentResponse> students(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) Integer year,
                                                  @RequestParam(required = false) String department,
                                                  @RequestParam(required = false) String section,
                                                  @RequestParam(required = false) Boolean active,
                                                  @RequestParam(required = false) Long courseId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "25") int size) {
        return studentService.list(q, year, department, section, active, courseId, page, size);
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse createStudent(@Valid @RequestBody CreateStudentRequest req) {
        return studentService.create(req);
    }

    @GetMapping("/students/attributes")
    public AttributeOptions attributes() {
        return studentService.attributes();
    }

    @GetMapping("/students/{id}")
    public StudentResponse student(@PathVariable Long id) {
        return studentService.get(id);
    }

    @PatchMapping("/students/{id}")
    public StudentResponse updateStudent(@PathVariable Long id, @Valid @RequestBody UpdateStudentRequest req) {
        return studentService.update(id, req);
    }

    /** mode=preview (default) validates only; mode=commit imports after explicit confirmation. */
    @PostMapping(value = "/students/import", consumes = "multipart/form-data")
    public RosterImportResult importStudents(@RequestPart("file") MultipartFile file,
                                             @RequestParam(defaultValue = "preview") String mode,
                                             @RequestParam(defaultValue = "false") boolean updateExisting,
                                             @RequestParam(defaultValue = "false") boolean skipInvalid) {
        return studentService.importRoster(file, "commit".equalsIgnoreCase(mode), updateExisting, skipInvalid);
    }

    // ----------------------------------------------------------------------- courses

    @GetMapping("/courses")
    public List<CourseResponse> courses() {
        return courseService.list();
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse createCourse(@Valid @RequestBody CourseRequest req) {
        return courseService.create(req);
    }

    @PatchMapping("/courses/{id}")
    public CourseResponse updateCourse(@PathVariable Long id, @Valid @RequestBody CourseUpdateRequest req) {
        return courseService.update(id, req);
    }

    @GetMapping("/courses/{id}/students")
    public List<StudentResponse> courseRoster(@PathVariable Long id) {
        return courseService.roster(id);
    }

    @PostMapping("/courses/{id}/students")
    public MembershipResult enroll(@PathVariable Long id, @Valid @RequestBody IdsOrRegisterNumbers req) {
        return courseService.enroll(id, req);
    }

    @DeleteMapping("/courses/{id}/students/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unenroll(@PathVariable Long id, @PathVariable Long studentId) {
        courseService.unenroll(id, studentId);
    }

    // ------------------------------------------------------------------------ groups

    @GetMapping("/groups")
    public List<GroupResponse> groups() {
        return groupService.list(CurrentUser.get());
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(@Valid @RequestBody GroupRequest req) {
        return groupService.create(req, CurrentUser.get());
    }

    @PostMapping("/groups/preview")
    public PreviewResponse previewGroup(@Valid @RequestBody PreviewRequest req) {
        return groupService.preview(req.filter());
    }

    @GetMapping("/groups/{id}")
    public GroupDetail group(@PathVariable Long id) {
        return groupService.get(id, CurrentUser.get());
    }

    @PatchMapping("/groups/{id}")
    public GroupResponse updateGroup(@PathVariable Long id, @Valid @RequestBody GroupUpdateRequest req) {
        return groupService.update(id, req, CurrentUser.get());
    }

    @DeleteMapping("/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable Long id) {
        groupService.delete(id, CurrentUser.get());
    }

    @PostMapping("/groups/{id}/members")
    public MembershipResult addMembers(@PathVariable Long id, @Valid @RequestBody IdsOrRegisterNumbers req) {
        return groupService.addMembers(id, req, CurrentUser.get());
    }

    @DeleteMapping("/groups/{id}/members/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long id, @PathVariable Long studentId) {
        groupService.removeMember(id, studentId, CurrentUser.get());
    }

    @PostMapping("/groups/{id}/refresh")
    public GroupDetail refresh(@PathVariable Long id) {
        return groupService.refresh(id, CurrentUser.get());
    }
}
