package com.quizsphere.repository;

import com.quizsphere.entity.Attempt;
import com.quizsphere.entity.AttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    List<Attempt> findByAssignmentIdAndStudentIdOrderByAttemptNumberAsc(Long assignmentId, Long studentId);

    @EntityGraph(attributePaths = {"assignment"})
    List<Attempt> findByStudentId(Long studentId);

    @EntityGraph(attributePaths = {"student", "student.user"})
    List<Attempt> findByAssignmentIdOrderByIdAsc(Long assignmentId);

    Optional<Attempt> findByAssignmentIdAndStudentIdAndStatus(Long assignmentId, Long studentId, AttemptStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a where a.id = :id")
    Optional<Attempt> lockById(Long id);

    @Query("select a.id from Attempt a where a.status = com.quizsphere.entity.AttemptStatus.IN_PROGRESS and a.endsAt < :cutoff")
    List<Long> findExpiredInProgress(Instant cutoff);

    @Query("select a.id from Attempt a where a.status = com.quizsphere.entity.AttemptStatus.IN_PROGRESS and a.fullscreenExitedAt < :cutoff")
    List<Long> findFullscreenCountdownExpired(Instant cutoff);

    @Query("select a.id from Attempt a where a.status = com.quizsphere.entity.AttemptStatus.IN_PROGRESS and a.assignment.id = :assignmentId")
    List<Long> findInProgressIdsByAssignment(Long assignmentId);

    boolean existsByAssignmentQuizId(Long quizId);

    boolean existsByAssignmentId(Long assignmentId);

    long countByAssignmentIdAndStatus(Long assignmentId, AttemptStatus status);

    @EntityGraph(attributePaths = {"student", "assignment", "assignment.quiz"})
    @Query("select a from Attempt a where a.assignment.owner.id = :ownerId and a.submittedAt is not null order by a.submittedAt desc")
    List<Attempt> findRecentSubmissions(Long ownerId, Pageable pageable);
}
