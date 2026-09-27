package com.quizsphere.repository;

import com.quizsphere.entity.ProctoringEvent;
import com.quizsphere.entity.ReviewStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProctoringEventRepository extends JpaRepository<ProctoringEvent, Long> {

    @EntityGraph(attributePaths = {"attempt", "attempt.student", "reviewedBy"})
    @Query("select e from ProctoringEvent e where e.attempt.assignment.id = :assignmentId order by e.occurredAt asc, e.id asc")
    List<ProctoringEvent> findByAssignmentId(Long assignmentId);

    List<ProctoringEvent> findByAttemptId(Long attemptId);

    long countByAttemptId(Long attemptId);

    @Query("""
            select count(e) from ProctoringEvent e
             where e.attempt.assignment.owner.id = :ownerId and e.reviewStatus = :status
               and e.eventType in (com.quizsphere.entity.ProctoringEventType.FOCUS_LOST,
                                   com.quizsphere.entity.ProctoringEventType.FULLSCREEN_EXIT)
            """)
    long countWarningsByOwnerAndStatus(Long ownerId, ReviewStatus status);
}
