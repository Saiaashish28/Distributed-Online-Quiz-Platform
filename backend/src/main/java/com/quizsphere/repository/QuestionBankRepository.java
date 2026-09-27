package com.quizsphere.repository;

import com.quizsphere.entity.QuestionBank;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {

    @EntityGraph(attributePaths = {"course"})
    List<QuestionBank> findByOwnerIdOrderByNameAsc(Long ownerId);

    @Query("select q.bank.id, count(q) from Question q where q.bank.owner.id = :ownerId group by q.bank.id")
    List<Object[]> countQuestionsByBank(Long ownerId);
}
