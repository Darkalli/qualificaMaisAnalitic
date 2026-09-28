package com.repositories;

import com.entities.Course;
import com.entities.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByid(Long id);

    Optional<Course> getById(long id);
}
