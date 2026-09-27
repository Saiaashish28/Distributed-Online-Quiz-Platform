package com.quizsphere.repository;

import com.quizsphere.entity.Question;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @EntityGraph(attributePaths = {"options"})
    @Query("select q from Question q where q.quiz.id = :quizId order by q.position, q.id")
    List<Question> findByQuizIdWithOptions(Long quizId);

    @EntityGraph(attributePaths = {"options"})
    @Query("select q from Question q where q.bank.id = :bankId order by q.position, q.id")
    List<Question> findByBankIdWithOptions(Long bankId);

    @EntityGraph(attributePaths = {"options"})
    @Query("select q from Question q where q.id in :ids")
    List<Question> findAllWithOptionsByIdIn(Collection<Long> ids);

    @Query("select coalesce(max(q.position), -1) from Question q where q.quiz.id = :quizId")
    int maxPositionInQuiz(Long quizId);

    @Query("select coalesce(max(q.position), -1) from Question q where q.bank.id = :bankId")
    int maxPositionInBank(Long bankId);
}
