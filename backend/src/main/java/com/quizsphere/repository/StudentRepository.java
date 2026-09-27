package com.quizsphere.repository;

import com.quizsphere.entity.Student;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    Optional<Student> findByRegisterNumber(String registerNumber);

    @EntityGraph(attributePaths = {"user"})
    Optional<Student> findByUserId(Long userId);

    boolean existsByRegisterNumber(String registerNumber);

    @EntityGraph(attributePaths = {"courses", "user"})
    List<Student> findByRegisterNumberIn(Collection<String> registerNumbers);

    @EntityGraph(attributePaths = {"courses", "user"})
    @Query("select s from Student s")
    List<Student> findAllWithCourses();

    @EntityGraph(attributePaths = {"courses", "user"})
    @Query("select s from Student s where s.id in :ids")
    List<Student> findAllWithCoursesByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = {"courses", "user"})
    @Query("select s from Student s where s.id = :id")
    Optional<Student> findWithCoursesById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id = :id")
    Optional<Student> lockById(Long id);

    @Query("select distinct s.department from Student s order by s.department")
    List<String> distinctDepartments();

    @Query("select distinct s.section from Student s where s.section is not null order by s.section")
    List<String> distinctSections();

    @Query("select distinct s.program from Student s where s.program is not null order by s.program")
    List<String> distinctPrograms();

    long countByActiveTrue();

    @EntityGraph(attributePaths = {"user"})
    @Query("select s from Student s join s.courses c where c.id = :courseId order by s.registerNumber")
    List<Student> findByCourseId(Long courseId);

    @Query("select c.id, count(s) from Student s join s.courses c group by c.id")
    List<Object[]> countByCourse();
}
