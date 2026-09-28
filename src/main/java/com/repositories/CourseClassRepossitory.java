package com.repositories;

import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseClassRepossitory extends JpaRepository<CourseClass, Long> {
    Optional<CourseClass> findByCourse(Course course);
}
