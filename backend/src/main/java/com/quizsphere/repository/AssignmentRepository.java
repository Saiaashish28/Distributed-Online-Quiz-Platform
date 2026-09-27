package com.quizsphere.repository;

import com.quizsphere.entity.Assignment;
import com.quizsphere.entity.AssignmentStatus;
import com.quizsphere.entity.SessionState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @EntityGraph(attributePaths = {"quiz", "quiz.course"})
    List<Assignment> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = {"quiz", "quiz.course", "groups", "students"})
    List<Assignment> findByStatus(AssignmentStatus status);

    @EntityGraph(attributePaths = {"quiz", "quiz.course", "groups", "students"})
    @Query("select a from Assignment a where a.id = :id")
    Optional<Assignment> findDetailedById(Long id);

    Optional<Assignment> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);

    boolean existsByQuizId(Long quizId);

    @EntityGraph(attributePaths = {"quiz", "quiz.course", "groups", "students"})
    List<Assignment> findByQuizIdOrderByCreatedAtAsc(Long quizId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :id")
    Optional<Assignment> lockById(Long id);

    @Query("select a.id from Assignment a where a.liveSession = true and a.sessionState = :state and a.sessionEndsAt < :cutoff")
    List<Long> findLiveSessionsEndedBefore(SessionState state, Instant cutoff);

    long countByOwnerIdAndStatus(Long ownerId, AssignmentStatus status);

    long countByOwnerIdAndSessionState(Long ownerId, SessionState state);
}
