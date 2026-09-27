package com.quizsphere.websocket;

import com.quizsphere.entity.Assignment;
import com.quizsphere.entity.AttemptStatus;
import com.quizsphere.entity.Student;
import com.quizsphere.repository.AssignmentRepository;
import com.quizsphere.repository.AttemptRepository;
import com.quizsphere.repository.StudentRepository;
import com.quizsphere.security.AuthUser;
import com.quizsphere.service.AssignmentRules;
import com.quizsphere.service.EligibilityService;
import com.quizsphere.service.ProgressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Authorizes WebSocket subscriptions and builds authoritative snapshots from the database. */
@Service
public class SessionSnapshotService {

    public record Access(Long assignmentId, String registerNumber, String name) {
    }

    private final AssignmentRepository assignmentRepository;
    private final AttemptRepository attemptRepository;
    private final StudentRepository studentRepository;
    private final EligibilityService eligibility;
    private final ProgressService progressService;
    private final RealtimeHub hub;

    public SessionSnapshotService(AssignmentRepository assignmentRepository, AttemptRepository attemptRepository,
                                  StudentRepository studentRepository, EligibilityService eligibility,
                                  ProgressService progressService, RealtimeHub hub) {
        this.assignmentRepository = assignmentRepository;
        this.attemptRepository = attemptRepository;
        this.studentRepository = studentRepository;
        this.eligibility = eligibility;
        this.progressService = progressService;
        this.hub = hub;
    }

    /** Admins may watch their own assignments; students only assignments they can see. */
    @Transactional(readOnly = true)
    public Optional<Access> authorize(AuthUser user, Long assignmentId) {
        Optional<Assignment> a = assignmentRepository.findDetailedById(assignmentId);
        if (a.isEmpty()) return Optional.empty();
        if (user.isAdmin()) {
            return a.get().getOwner().getId().equals(user.userId())
                    ? Optional.of(new Access(assignmentId, null, user.name())) : Optional.empty();
        }
        if (user.studentId() == null || !eligibility.canView(a.get(), eligibility.context(user.studentId()))) {
            return Optional.empty();
        }
        Student s = studentRepository.findById(user.studentId()).orElseThrow();
        return Optional.of(new Access(assignmentId, s.getRegisterNumber(), s.getFullName()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(AuthUser user, Long assignmentId) {
        Assignment a = assignmentRepository.findDetailedById(assignmentId).orElseThrow();
        Instant now = Instant.now();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("liveSession", a.isLiveSession());
        data.put("sessionState", a.getSessionState());
        data.put("sessionStartedAt", a.getSessionStartedAt());
        data.put("sessionEndsAt", a.getSessionEndsAt());
        data.put("status", a.getStatus());
        data.put("resultsVisible", AssignmentRules.resultsVisible(a, now));
        data.put("participantCount", hub.participantCount(assignmentId));
        if (user.isAdmin()) {
            data.put("participants", hub.participants(assignmentId));
            data.put("progress", progressService.progress(a));
        } else {
            attemptRepository.findByAssignmentIdAndStudentIdAndStatus(assignmentId, user.studentId(), AttemptStatus.IN_PROGRESS)
                    .ifPresent(at -> data.put("attempt", Map.of("id", at.getId(), "status", at.getStatus(),
                            "endsAt", at.getEndsAt())));
        }
        return data;
    }
}
