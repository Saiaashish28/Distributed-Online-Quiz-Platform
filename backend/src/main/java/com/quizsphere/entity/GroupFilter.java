package com.quizsphere.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Filter definition of a student group, stored as JSONB. Each non-empty list must
 * contain the student's value (string comparison is case-insensitive). Course
 * filtering matches students enrolled in ANY (default) or ALL of the listed courses.
 */
public record GroupFilter(
        List<Integer> academicYears,
        List<String> departments,
        List<String> sections,
        List<String> programs,
        List<Integer> semesters,
        List<Long> courseIds,
        CourseMatch courseMatch) {

    public enum CourseMatch { ANY, ALL }

    public GroupFilter {
        academicYears = clean(academicYears);
        departments = cleanStrings(departments);
        sections = cleanStrings(sections);
        programs = cleanStrings(programs);
        semesters = clean(semesters);
        courseIds = clean(courseIds);
        courseMatch = courseMatch == null ? CourseMatch.ANY : courseMatch;
    }

    @JsonIgnore
    public boolean hasAcademicCriteria() {
        return !academicYears.isEmpty() || !departments.isEmpty() || !sections.isEmpty()
                || !programs.isEmpty() || !semesters.isEmpty();
    }

    @JsonIgnore
    public boolean hasCourseCriteria() {
        return !courseIds.isEmpty();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return !hasAcademicCriteria() && !hasCourseCriteria();
    }

    public boolean matches(Student s, Set<Long> studentCourseIds) {
        if (!academicYears.isEmpty() && !academicYears.contains(s.getAcademicYear())) return false;
        if (!semesters.isEmpty() && !semesters.contains(s.getSemester())) return false;
        if (!containsIgnoreCase(departments, s.getDepartment())) return false;
        if (!containsIgnoreCase(sections, s.getSection())) return false;
        if (!containsIgnoreCase(programs, s.getProgram())) return false;
        if (!courseIds.isEmpty()) {
            return courseMatch == CourseMatch.ALL
                    ? studentCourseIds.containsAll(courseIds)
                    : courseIds.stream().anyMatch(studentCourseIds::contains);
        }
        return true;
    }

    private static boolean containsIgnoreCase(List<String> allowed, String value) {
        if (allowed.isEmpty()) return true;
        if (value == null) return false;
        return allowed.stream().anyMatch(a -> a.equalsIgnoreCase(value.trim()));
    }

    private static <T> List<T> clean(Collection<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static List<String> cleanStrings(Collection<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(Objects::nonNull).map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
    }
}
