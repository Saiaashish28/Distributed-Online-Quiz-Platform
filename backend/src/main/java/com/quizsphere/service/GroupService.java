package com.quizsphere.service;

import com.quizsphere.dto.CourseGroupDtos.*;
import com.quizsphere.dto.StudentDtos.IdsOrRegisterNumbers;
import com.quizsphere.dto.StudentDtos.MembershipResult;
import com.quizsphere.dto.StudentDtos.StudentResponse;
import com.quizsphere.entity.*;
import com.quizsphere.exception.ApiException;
import com.quizsphere.repository.CourseRepository;
import com.quizsphere.repository.StudentGroupRepository;
import com.quizsphere.repository.StudentRepository;
import com.quizsphere.repository.UserRepository;
import com.quizsphere.security.AuthUser;
import com.quizsphere.util.Text;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Academic, course-based and custom student groups. DYNAMIC groups compute membership from
 * the filter at read time, so they always reflect current attributes and enrollments; MANUAL
 * groups store explicit members.
 */
@Service
public class GroupService {

    private static final int PREVIEW_LIMIT = 500;

    private final StudentGroupRepository groupRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseService.StudentLookup studentLookup;

    public GroupService(StudentGroupRepository groupRepository, StudentRepository studentRepository,
                        CourseRepository courseRepository, UserRepository userRepository,
                        CourseService.StudentLookup studentLookup) {
        this.groupRepository = groupRepository;
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.studentLookup = studentLookup;
    }

    // ------------------------------------------------------------ membership resolution

    public static Set<Long> courseIds(Student s) {
        return s.getCourses().stream().map(Course::getId).collect(Collectors.toSet());
    }

    /** Members of a group; {@code allStudents} must have courses loaded (used for DYNAMIC groups). */
    public List<Student> members(StudentGroup g, List<Student> allStudents) {
        if (g.getMembershipMode() == MembershipMode.MANUAL) {
            return new ArrayList<>(g.getMembers());
        }
        GroupFilter f = g.getFilterDefinition();
        if (f == null || f.isEmpty()) return List.of();
        return allStudents.stream().filter(s -> f.matches(s, courseIds(s))).toList();
    }

    // ------------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<GroupResponse> list(AuthUser user) {
        List<StudentGroup> groups = groupRepository.findAllOrdered();
        Map<Long, Long> manualCounts = groupRepository.countManualMembers().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        List<Student> all = groups.stream().anyMatch(g -> g.getMembershipMode() == MembershipMode.DYNAMIC)
                ? studentRepository.findAllWithCourses() : List.of();
        Map<Long, String> courseCodes = courseCodes();
        return groups.stream().map(g -> {
            long count = g.getMembershipMode() == MembershipMode.MANUAL
                    ? manualCounts.getOrDefault(g.getId(), 0L) : members(g, all).size();
            return toResponse(g, count, user, courseCodes);
        }).toList();
    }

    @Transactional(readOnly = true)
    public GroupDetail get(Long id, AuthUser user) {
        StudentGroup g = find(id);
        List<Student> members = members(g, g.getMembershipMode() == MembershipMode.DYNAMIC
                ? studentRepository.findAllWithCourses() : List.of());
        List<StudentResponse> rows = members.stream()
                .sorted(Comparator.comparing(Student::getRegisterNumber)).map(StudentResponse::of).toList();
        return new GroupDetail(toResponse(g, rows.size(), user, courseCodes()), rows);
    }

    @Transactional(readOnly = true)
    public PreviewResponse preview(GroupFilter filter) {
        validateCourses(filter);
        if (filter.isEmpty()) {
            return new PreviewResponse(0, List.of());
        }
        List<Student> matched = studentRepository.findAllWithCourses().stream()
                .filter(s -> filter.matches(s, courseIds(s)))
                .sorted(Comparator.comparing(Student::getRegisterNumber)).toList();
        return new PreviewResponse(matched.size(),
                matched.stream().limit(PREVIEW_LIMIT).map(StudentResponse::of).toList());
    }

    // ---------------------------------------------------------------------- mutations

    @Transactional
    public GroupResponse create(GroupRequest req, AuthUser user) {
        String name = req.name().trim();
        if (groupRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A group named '" + name + "' already exists");
        }
        GroupFilter filter = req.filter() == null ? new GroupFilter(null, null, null, null, null, null, null) : req.filter();
        validateFilter(req.groupType(), req.membershipMode(), filter);

        StudentGroup g = new StudentGroup();
        g.setName(name);
        g.setDescription(Text.trimToNull(req.description()));
        g.setGroupType(req.groupType());
        g.setMembershipMode(req.membershipMode());
        g.setFilterDefinition(filter.isEmpty() ? null : filter);
        g.setOwner(userRepository.getReferenceById(user.userId()));

        if (req.membershipMode() == MembershipMode.MANUAL) {
            if (req.seedFromFilter() && !filter.isEmpty()) {
                studentRepository.findAllWithCourses().stream()
                        .filter(s -> filter.matches(s, courseIds(s)))
                        .forEach(g.getMembers()::add);
            }
            if (req.studentIds() != null && !req.studentIds().isEmpty()) {
                g.getMembers().addAll(studentRepository.findAllById(new HashSet<>(req.studentIds())));
            }
        }
        groupRepository.saveAndFlush(g);
        return toResponse(g, g.getMembershipMode() == MembershipMode.MANUAL ? g.getMembers().size()
                : members(g, studentRepository.findAllWithCourses()).size(), user, courseCodes());
    }

    @Transactional
    public GroupResponse update(Long id, GroupUpdateRequest req, AuthUser user) {
        StudentGroup g = findOwned(id, user);
        if (req.name() != null && !req.name().trim().equalsIgnoreCase(g.getName())) {
            if (groupRepository.existsByNameIgnoreCase(req.name().trim())) {
                throw ApiException.conflict("A group named '" + req.name().trim() + "' already exists");
            }
            g.setName(req.name().trim());
        } else if (req.name() != null) {
            g.setName(req.name().trim());
        }
        if (req.description() != null) g.setDescription(Text.trimToNull(req.description()));
        if (req.filter() != null) {
            validateFilter(g.getGroupType(), g.getMembershipMode(), req.filter());
            g.setFilterDefinition(req.filter().isEmpty() ? null : req.filter());
        }
        groupRepository.saveAndFlush(g);
        return get(id, user).group();
    }

    @Transactional
    public void delete(Long id, AuthUser user) {
        StudentGroup g = findOwned(id, user);
        if (groupRepository.isUsedByAssignment(id)) {
            throw ApiException.conflict("This group is used by one or more assignments and cannot be deleted");
        }
        groupRepository.delete(g);
    }

    @Transactional
    public MembershipResult addMembers(Long id, IdsOrRegisterNumbers req, AuthUser user) {
        StudentGroup g = findOwned(id, user);
        requireManual(g);
        CourseService.StudentLookup.Resolved resolved = studentLookup.resolve(req);
        int added = 0, already = 0;
        for (Student s : resolved.students()) {
            if (g.getMembers().add(s)) added++;
            else already++;
        }
        return new MembershipResult(added, already, resolved.notFound());
    }

    @Transactional
    public void removeMember(Long id, Long studentId, AuthUser user) {
        StudentGroup g = findOwned(id, user);
        requireManual(g);
        if (!g.getMembers().removeIf(s -> s.getId().equals(studentId))) {
            throw ApiException.notFound("Group member");
        }
    }

    /** DYNAMIC groups are always current; refresh simply recomputes and returns the member list. */
    @Transactional(readOnly = true)
    public GroupDetail refresh(Long id, AuthUser user) {
        return get(id, user);
    }

    // ------------------------------------------------------------------------ helpers

    private void requireManual(StudentGroup g) {
        if (g.getMembershipMode() != MembershipMode.MANUAL) {
            throw ApiException.badRequest("Members of a dynamic group come from its filter; edit the filter instead");
        }
    }

    private void validateFilter(GroupType type, MembershipMode mode, GroupFilter f) {
        validateCourses(f);
        if (type == GroupType.ACADEMIC && f.hasCourseCriteria()) {
            throw ApiException.badRequest("Academic groups cannot filter by course; use a course-based group");
        }
        if (mode == MembershipMode.DYNAMIC) {
            if (type == GroupType.ACADEMIC && !f.hasAcademicCriteria()) {
                throw ApiException.badRequest("A dynamic academic group needs at least one of year, department, section, program or semester");
            }
            if (type == GroupType.COURSE_BASED && !f.hasCourseCriteria()) {
                throw ApiException.badRequest("A dynamic course-based group needs at least one course");
            }
            if (f.isEmpty()) {
                throw ApiException.badRequest("A dynamic group needs a filter");
            }
        }
    }

    private void validateCourses(GroupFilter f) {
        if (f.hasCourseCriteria() && courseRepository.findAllById(f.courseIds()).size() != f.courseIds().size()) {
            throw ApiException.badRequest("One or more courses in the filter do not exist");
        }
    }

    private StudentGroup find(Long id) {
        return groupRepository.findWithMembersById(id).orElseThrow(() -> ApiException.notFound("Group"));
    }

    private StudentGroup findOwned(Long id, AuthUser user) {
        StudentGroup g = find(id);
        if (!g.getOwner().getId().equals(user.userId())) {
            throw ApiException.forbidden("Only the group's owner can modify it");
        }
        return g;
    }

    private Map<Long, String> courseCodes() {
        return courseRepository.findAll().stream().collect(Collectors.toMap(Course::getId, Course::getCourseCode));
    }

    private GroupResponse toResponse(StudentGroup g, long count, AuthUser user, Map<Long, String> courseCodes) {
        return new GroupResponse(g.getId(), g.getName(), g.getDescription(), g.getGroupType(), g.getMembershipMode(),
                g.getFilterDefinition(), summarize(g.getFilterDefinition(), courseCodes), count, g.getOwner().getName(),
                g.getOwner().getId().equals(user.userId()), groupRepository.isUsedByAssignment(g.getId()),
                g.getCreatedAt());
    }

    public static String summarize(GroupFilter f, Map<Long, String> courseCodes) {
        if (f == null || f.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        if (!f.academicYears().isEmpty()) parts.add("Year " + f.academicYears().stream().map(Text::roman).collect(Collectors.joining("/")));
        if (!f.departments().isEmpty()) parts.add(String.join("/", f.departments()));
        if (!f.programs().isEmpty()) parts.add("Program " + String.join("/", f.programs()));
        if (!f.sections().isEmpty()) parts.add("Section " + String.join("/", f.sections()));
        if (!f.semesters().isEmpty()) parts.add("Sem " + f.semesters().stream().map(String::valueOf).collect(Collectors.joining("/")));
        if (!f.courseIds().isEmpty()) {
            parts.add((f.courseMatch() == GroupFilter.CourseMatch.ALL ? "All of " : "Any of ")
                    + f.courseIds().stream().map(id -> courseCodes.getOrDefault(id, "#" + id)).collect(Collectors.joining(", ")));
        }
        return String.join(" · ", parts);
    }
}
