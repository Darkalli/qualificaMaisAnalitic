package com.repositories;

import com.entities.CourseClass;
import com.entities.Person;
import com.entities.Presence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PresenceRepository extends JpaRepository<Presence, Long> {

    List<Presence> findByPersonId(Long personId);

    List<Presence> findByCourseClassIdAndCourseId(Long courseClassId, Long courseId);

    Optional<Presence> findByCourseClassIdAndPersonId(Long courseClassId, Long personId);

    boolean existsByPersonAndCourseClass(Person person, CourseClass courseClass);
}
