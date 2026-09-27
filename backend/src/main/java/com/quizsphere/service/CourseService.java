package com.quizsphere.service;

import com.quizsphere.dto.CourseGroupDtos.*;
import com.quizsphere.dto.StudentDtos.IdsOrRegisterNumbers;
import com.quizsphere.dto.StudentDtos.MembershipResult;
import com.quizsphere.dto.StudentDtos.StudentResponse;
import com.quizsphere.entity.Course;
import com.quizsphere.entity.Student;
import com.quizsphere.exception.ApiException;
import com.quizsphere.repository.CourseRepository;
import com.quizsphere.repository.StudentRepository;
import com.quizsphere.util.Text;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final StudentLookup studentLookup;

    public CourseService(CourseRepository courseRepository, StudentRepository studentRepository,
                         StudentLookup studentLookup) {
        this.courseRepository = courseRepository;
        this.studentRepository = studentRepository;
        this.studentLookup = studentLookup;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> list() {
        Map<Long, Long> counts = studentRepository.countByCourse().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        return courseRepository.findAll().stream()
                .sorted(Comparator.comparing(Course::getCourseCode))
                .map(c -> toResponse(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CourseResponse create(CourseRequest req) {
        String code = req.courseCode().trim().toUpperCase(Locale.ROOT);
        if (courseRepository.existsByCourseCodeIgnoreCase(code)) {
            throw ApiException.conflict("Course code " + code + " already exists");
        }
        Course c = new Course();
        c.setCourseCode(code);
        c.setCourseName(req.courseName().trim());
        c.setSemester(req.semester());
        return toResponse(courseRepository.saveAndFlush(c), 0);
    }

    @Transactional
    public CourseResponse update(Long id, CourseUpdateRequest req) {
        Course c = find(id);
        if (req.courseName() != null) c.setCourseName(req.courseName().trim());
        if (req.semester() != null) c.setSemester(req.semester());
        return toResponse(c, studentRepository.findByCourseId(id).size());
    }

    @Transactional(readOnly = true)
    public List<StudentResponse> roster(Long id) {
        find(id);
        List<Long> ids = studentRepository.findByCourseId(id).stream().map(Student::getId).toList();
        return studentRepository.findAllWithCoursesByIdIn(ids).stream()
                .sorted(Comparator.comparing(Student::getRegisterNumber))
                .map(StudentResponse::of).toList();
    }

    @Transactional
    public MembershipResult enroll(Long id, IdsOrRegisterNumbers req) {
        Course course = find(id);
        StudentLookup.Resolved resolved = studentLookup.resolve(req);
        int added = 0, already = 0;
        for (Student s : resolved.students()) {
            if (s.getCourses().add(course)) added++;
            else already++;
        }
        return new MembershipResult(added, already, resolved.notFound());
    }

    @Transactional
    public void unenroll(Long id, Long studentId) {
        Course course = find(id);
        Student s = studentRepository.findWithCoursesById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
        if (!s.getCourses().remove(course)) {
            throw ApiException.notFound("Enrollment");
        }
    }

    private Course find(Long id) {
        return courseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Course"));
    }

    private static CourseResponse toResponse(Course c, long count) {
        return new CourseResponse(c.getId(), c.getCourseCode(), c.getCourseName(), c.getSemester(), count,
                c.getCreatedAt());
    }

    /** Helper for resolving student ids / register numbers given in admin requests. */
    @org.springframework.stereotype.Component
    public static class StudentLookup {
        private final StudentRepository studentRepository;

        public StudentLookup(StudentRepository studentRepository) {
            this.studentRepository = studentRepository;
        }

        public record Resolved(List<Student> students, List<String> notFound) {
        }

        public Resolved resolve(IdsOrRegisterNumbers req) {
            Map<Long, Student> result = new LinkedHashMap<>();
            List<String> notFound = new ArrayList<>();
            if (req.studentIds() != null && !req.studentIds().isEmpty()) {
                Set<Long> ids = new LinkedHashSet<>(req.studentIds());
                List<Student> found = studentRepository.findAllWithCoursesByIdIn(ids);
                found.forEach(s -> result.put(s.getId(), s));
                ids.stream().filter(i -> !result.containsKey(i)).forEach(i -> notFound.add("#" + i));
            }
            if (req.registerNumbers() != null && !req.registerNumbers().isEmpty()) {
                Set<String> regNos = req.registerNumbers().stream().map(Text::normalizeRegisterNumber)
                        .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
                Map<String, Student> found = studentRepository.findByRegisterNumberIn(regNos).stream()
                        .collect(Collectors.toMap(Student::getRegisterNumber, s -> s));
                for (String r : regNos) {
                    Student s = found.get(r);
                    if (s == null) notFound.add(r);
                    else result.putIfAbsent(s.getId(), s);
                }
            }
            return new Resolved(new ArrayList<>(result.values()), notFound);
        }
    }
}
