package com.repositories;

import com.entities.Course;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForRegistration(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(Long id);

    Optional<Course> findByid(Long id);

    Optional<Course> getById(long id);

    Optional<Course> findByName(String name);
}
