package com.quizsphere.service;

import com.quizsphere.entity.*;
import com.quizsphere.exception.ApiException;
import com.quizsphere.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Decides which students may see and take an assignment. Joining with a code never bypasses
 * group/individual targeting unless the admin explicitly enabled open access for that code.
 */
@Service
public class EligibilityService {

    private final StudentRepository studentRepository;
    private final StudentGroupRepository groupRepository;
    private final AssignmentJoinRepository joinRepository;
    private final AttemptRepository attemptRepository;
    private final GroupService groupService;

    public EligibilityService(StudentRepository studentRepository, StudentGroupRepository groupRepository,
                              AssignmentJoinRepository joinRepository, AttemptRepository attemptRepository,
                              GroupService groupService) {
        this.studentRepository = studentRepository;
        this.groupRepository = groupRepository;
        this.joinRepository = joinRepository;
        this.attemptRepository = attemptRepository;
        this.groupService = groupService;
    }

    public record StudentContext(Student student, Set<Long> courseIds, Set<Long> manualGroupIds,
                                 Set<Long> joinedAssignmentIds, Set<Long> attemptedAssignmentIds) {
    }

    @Transactional(readOnly = true)
    public StudentContext context(Long studentId) {
        Student s = studentRepository.findWithCoursesById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
        Set<Long> attempted = attemptRepository.findByStudentId(studentId).stream()
                .map(a -> a.getAssignment().getId()).collect(Collectors.toSet());
        return new StudentContext(s, GroupService.courseIds(s), groupRepository.findManualGroupIdsByStudentId(studentId),
                joinRepository.findAssignmentIdsByStudentId(studentId), attempted);
    }

    public boolean inGroup(StudentGroup g, StudentContext ctx) {
        if (g.getMembershipMode() == MembershipMode.MANUAL) {
            return ctx.manualGroupIds().contains(g.getId());
        }
        GroupFilter f = g.getFilterDefinition();
        return f != null && !f.isEmpty() && f.matches(ctx.student(), ctx.courseIds());
    }

    /** Targeted by the assignment's audience (open, individual or group), ignoring join codes. */
    public boolean isTargeted(Assignment a, StudentContext ctx) {
        if (a.getType() == AssignmentType.OPEN) return true;
        Long sid = ctx.student().getId();
        if (a.getStudents().stream().anyMatch(s -> s.getId().equals(sid))) return true;
        return a.getGroups().stream().anyMatch(g -> inGroup(g, ctx));
    }

    /** May the student see this assignment on their dashboard and open it? */
    public boolean canView(Assignment a, StudentContext ctx) {
        if (ctx.attemptedAssignmentIds().contains(a.getId())) return true;
        if (!ctx.student().isActive()) return false;
        if (a.getType() == AssignmentType.CODE) {
            return ctx.joinedAssignmentIds().contains(a.getId()) && canJoinWithCode(a, ctx);
        }
        return isTargeted(a, ctx);
    }

    public boolean canJoinWithCode(Assignment a, StudentContext ctx) {
        return a.isCodeOpenAccess() || isTargeted(a, ctx);
    }

    /**
     * Every student the assignment is assigned to (for monitoring, results and marksheets),
     * including students who never started, plus anyone who has an attempt.
     */
    @Transactional(readOnly = true)
    public List<Student> assignedStudents(Assignment a) {
        Map<Long, Student> result = new HashMap<>();
        List<Student> all = null;
        boolean needAll = a.getType() == AssignmentType.OPEN
                || a.getGroups().stream().anyMatch(g -> g.getMembershipMode() == MembershipMode.DYNAMIC);
        if (needAll) all = studentRepository.findAllWithCourses();

        if (a.getType() == AssignmentType.OPEN) {
            all.stream().filter(Student::isActive).forEach(s -> result.put(s.getId(), s));
        } else {
            a.getStudents().forEach(s -> result.put(s.getId(), s));
            for (StudentGroup g : a.getGroups()) {
                StudentGroup loaded = g.getMembershipMode() == MembershipMode.MANUAL
                        ? groupRepository.findWithMembersById(g.getId()).orElse(g) : g;
                groupService.members(loaded, all == null ? List.of() : all).forEach(s -> result.put(s.getId(), s));
            }
            if (a.getType() == AssignmentType.CODE && a.isCodeOpenAccess()) {
                studentRepository.findAllById(joinRepository.findStudentIdsByAssignmentId(a.getId()))
                        .forEach(s -> result.put(s.getId(), s));
            }
        }
        result.values().removeIf(s -> !s.isActive());
        for (Attempt at : attemptRepository.findByAssignmentIdOrderByIdAsc(a.getId())) {
            result.putIfAbsent(at.getStudent().getId(), at.getStudent());
        }
        return result.values().stream().sorted(Comparator.comparing(Student::getRegisterNumber)).toList();
    }
}
