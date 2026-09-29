package com.repositories;

import com.entities.Course;
import com.entities.Person;
import com.entities.Presence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PresenceRepository extends JpaRepository<Presence, Long> {

    List<Presence> findByPersonId(Long personId);

    List<Presence> findByDateAndCourseId(LocalDate date, Long courseId);
    Optional<Presence> findByDateAndPersonIdAndCourseId(LocalDate date, Long personId, Long courseId);
}
