package com.quizsphere.dto;

import com.quizsphere.entity.Course;
import com.quizsphere.entity.Student;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class StudentDtos {

    private StudentDtos() {
    }

    public record CourseRef(Long id, String courseCode, String courseName) {
        public static CourseRef of(Course c) {
            return c == null ? null : new CourseRef(c.getId(), c.getCourseCode(), c.getCourseName());
        }
    }

    public record StudentRef(Long id, String registerNumber, String fullName) {
        public static StudentRef of(Student s) {
            return new StudentRef(s.getId(), s.getRegisterNumber(), s.getFullName());
        }
    }

    public record CreateStudentRequest(
            @NotBlank @Size(max = 40) String registerNumber,
            @NotBlank @Size(max = 150) String fullName,
            @Email @Size(max = 255) String email,
            @NotNull @Min(1) @Max(6) Integer academicYear,
            @NotBlank @Size(max = 50) String department,
            @Size(max = 20) String section,
            @Size(max = 50) String program,
            @Min(1) @Max(12) Integer semester,
            @Size(min = 8, max = 72, message = "must be 8-72 characters") String password,
            List<Long> courseIds) {
    }

    public record UpdateStudentRequest(
            @Size(min = 1, max = 150) String fullName,
            @Email @Size(max = 255) String email,
            @Min(1) @Max(6) Integer academicYear,
            @Size(min = 1, max = 50) String department,
            @Size(max = 20) String section,
            @Size(max = 50) String program,
            @Min(1) @Max(12) Integer semester,
            Boolean active,
            List<Long> courseIds,
            @Size(min = 8, max = 72, message = "must be 8-72 characters") String resetPassword) {
    }

    public record StudentResponse(Long id, String registerNumber, String fullName, String email,
                                  Integer academicYear, String department, String section, String program,
                                  Integer semester, boolean active, List<CourseRef> courses, Instant createdAt) {
        public static StudentResponse of(Student s) {
            return new StudentResponse(s.getId(), s.getRegisterNumber(), s.getFullName(), s.getUser().getEmail(),
                    s.getAcademicYear(), s.getDepartment(), s.getSection(), s.getProgram(), s.getSemester(),
                    s.isActive(),
                    s.getCourses().stream().sorted(Comparator.comparing(Course::getCourseCode)).map(CourseRef::of).toList(),
                    s.getCreatedAt());
        }
    }

    public record AttributeOptions(List<String> departments, List<String> sections, List<String> programs) {
    }

    public enum RowAction { CREATE, UPDATE, SKIP_EXISTING, INVALID }

    public record RosterImportRow(int rowNumber, String registerNumber, String fullName, RowAction action,
                                  List<String> errors, List<String> warnings, Map<String, String> values) {
    }

    public record RosterImportResult(String fileName, boolean committed, int totalRows, int toCreate, int toUpdate,
                                     int skippedExisting, int invalid, List<String> headerErrors,
                                     List<RosterImportRow> rows) {
    }

    public record IdsOrRegisterNumbers(List<Long> studentIds, List<@Size(max = 40) String> registerNumbers) {
    }

    public record MembershipResult(int added, int alreadyPresent, List<String> notFound) {
    }
}
