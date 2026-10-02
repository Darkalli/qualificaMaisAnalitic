package com.repositories;

import com.entities.Course;
import com.entities.CourseClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CourseClassRepository extends JpaRepository<CourseClass, Long> {
    List<CourseClass> findByCourse(Course course);
    Optional<CourseClass> findById(Long id);
    @Query("select c.course.id from CourseClass c where c.id = :id")
    Optional<Long> findCourseIdById(Long id);

    boolean existsByCourseAndDay(Course course, LocalDate day);
    boolean existsByCourseAndDayAndIdNot(Course course, LocalDate day, Long id);
}
