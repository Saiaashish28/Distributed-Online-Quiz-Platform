package com.quizsphere.service;

import com.quizsphere.dto.AssignmentDtos.ProgressCounts;
import com.quizsphere.entity.Assignment;
import com.quizsphere.entity.Attempt;
import com.quizsphere.entity.AttemptStatus;
import com.quizsphere.entity.Student;
import com.quizsphere.repository.AssignmentRepository;
import com.quizsphere.repository.AttemptRepository;
import com.quizsphere.websocket.RealtimeHub;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Submission progress per assignment, used by the admin monitor and WebSocket events. */
@Service
public class ProgressService {

    private final EligibilityService eligibility;
    private final AttemptRepository attemptRepository;
    private final AssignmentRepository assignmentRepository;
    private final RealtimeHub hub;

    public ProgressService(EligibilityService eligibility, AttemptRepository attemptRepository,
                           AssignmentRepository assignmentRepository, RealtimeHub hub) {
        this.eligibility = eligibility;
        this.attemptRepository = attemptRepository;
        this.assignmentRepository = assignmentRepository;
        this.hub = hub;
    }

    @Transactional(readOnly = true)
    public ProgressCounts progress(Assignment a) {
        List<Student> assigned = eligibility.assignedStudents(a);
        Map<Long, List<Attempt>> byStudent = attemptRepository.findByAssignmentIdOrderByIdAsc(a.getId()).stream()
                .collect(Collectors.groupingBy(x -> x.getStudent().getId()));
        int inProgress = 0, submitted = 0, auto = 0, notStarted = 0;
        for (Student s : assigned) {
            Optional<Attempt> counted = AssignmentRules.counted(byStudent.getOrDefault(s.getId(), List.of()));
            if (counted.isEmpty()) notStarted++;
            else if (counted.get().getStatus() == AttemptStatus.IN_PROGRESS) inProgress++;
            else if (counted.get().getStatus() == AttemptStatus.SUBMITTED) submitted++;
            else auto++;
        }
        return new ProgressCounts(assigned.size(), notStarted, inProgress, submitted, auto,
                hub.participantCount(a.getId()));
    }

    /** Pushes updated counts to admins monitoring the assignment (after commit). */
    @Transactional(readOnly = true)
    public void publish(Long assignmentId) {
        assignmentRepository.findDetailedById(assignmentId)
                .ifPresent(a -> hub.toAdmins(assignmentId, "submission_progress", progress(a)));
    }
}
