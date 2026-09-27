package com.quizsphere.repository;

import com.quizsphere.entity.Quiz;
import com.quizsphere.entity.QuizStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    @EntityGraph(attributePaths = {"course"})
    List<Quiz> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    long countByOwnerIdAndStatus(Long ownerId, QuizStatus status);

    @Query("select q.quiz.id, count(q), sum(q.points) from Question q where q.quiz.owner.id = :ownerId group by q.quiz.id")
    List<Object[]> questionStatsByOwner(Long ownerId);
}
