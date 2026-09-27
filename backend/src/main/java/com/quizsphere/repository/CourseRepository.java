package com.quizsphere.repository;

import com.quizsphere.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCourseCodeIgnoreCase(String courseCode);

    boolean existsByCourseCodeIgnoreCase(String courseCode);
}
