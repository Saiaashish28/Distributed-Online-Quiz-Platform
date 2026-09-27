package com.quizsphere.repository;

import com.quizsphere.entity.AssignmentJoin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;

public interface AssignmentJoinRepository extends JpaRepository<AssignmentJoin, Long> {

    boolean existsByAssignmentIdAndStudentId(Long assignmentId, Long studentId);

    @Query("select j.assignment.id from AssignmentJoin j where j.student.id = :studentId")
    Set<Long> findAssignmentIdsByStudentId(Long studentId);

    @Query("select j.student.id from AssignmentJoin j where j.assignment.id = :assignmentId")
    List<Long> findStudentIdsByAssignmentId(Long assignmentId);
}
