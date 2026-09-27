package com.quizsphere.repository;

import com.quizsphere.entity.StudentGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StudentGroupRepository extends JpaRepository<StudentGroup, Long> {

    boolean existsByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = {"owner"})
    @Query("select g from StudentGroup g order by g.name")
    List<StudentGroup> findAllOrdered();

    @EntityGraph(attributePaths = {"members", "owner"})
    @Query("select g from StudentGroup g where g.id = :id")
    Optional<StudentGroup> findWithMembersById(Long id);

    @Query("select count(a) > 0 from Assignment a join a.groups g where g.id = :groupId")
    boolean isUsedByAssignment(Long groupId);

    @Query("select g.id from StudentGroup g join g.members m where m.id = :studentId")
    java.util.Set<Long> findManualGroupIdsByStudentId(Long studentId);

    @Query("select g.id, count(m) from StudentGroup g join g.members m group by g.id")
    List<Object[]> countManualMembers();
}
