package com.repositories;

import com.entities.Course;
import com.entities.CourseClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CourseClassRepository extends JpaRepository<CourseClass, Long> {
    List<CourseClass> findByCourse(Course course);
    Optional<CourseClass> findById(Long id);

    boolean existsByCourseAndDay(Course course, LocalDate day);
}
