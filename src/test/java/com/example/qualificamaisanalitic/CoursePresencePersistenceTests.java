package com.example.qualificamaisanalitic;

import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Presence;
import com.enums.PresenceStatus;
import com.repositories.PresenceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoursePresencePersistenceTests {
    @Autowired private EntityManager entityManager;
    @Autowired private PresenceRepository presences;

    @Test
    void savesAndReloadsClassesAndSeparatesAttendanceByCourseOnTheSameDay() {
        var day = LocalDate.of(2026, 9, 29);
        var person = PersonTestData.person("98765432100");
        entityManager.persist(person);
        var course = new Course("Informática", "Curso de exemplo", day, day.plusMonths(1));
        var otherCourse = new Course("Inglês", null, day, day.plusMonths(1));
        entityManager.persist(course);
        entityManager.persist(otherCourse);
        var courseClass = new CourseClass(day, "Manhã", day.atTime(8, 0), day.atTime(10, 0), course);
        entityManager.persist(courseClass);
        var otherClass = new CourseClass(day, "Tarde", day.atTime(14, 0), day.atTime(16, 0), otherCourse);
        entityManager.persist(otherClass);
        var attendance = new Presence(person, courseClass, course, PresenceStatus.PRESENT);
        var otherAttendance = new Presence(person, otherClass, otherCourse, PresenceStatus.ABSENT);
        entityManager.persist(attendance);
        entityManager.persist(otherAttendance);
        entityManager.flush();
        assertNotNull(course.getId());
        assertNotNull(courseClass.getId());
        assertNotNull(attendance.getId());
        entityManager.clear();

        var savedCourse = entityManager.find(Course.class, course.getId());
        assertEquals("Informática", savedCourse.getName());
        assertEquals(day.plusMonths(1), savedCourse.getFinish());
        assertEquals(1, savedCourse.getCourseClass().size());
        var savedClass = savedCourse.getCourseClass().getFirst();
        assertEquals(courseClass.getId(), savedClass.getId());
        assertEquals(day, savedClass.getDay());
        assertEquals(day.atTime(8, 0), savedClass.getStart());
        assertEquals(day.atTime(10, 0), savedClass.getFinish());

        var savedAttendance = entityManager.find(Presence.class, attendance.getId());
        assertEquals(attendance.getId(), savedAttendance.getId());
        assertEquals(courseClass.getId(), savedAttendance.getCourseClass().getId());
        assertEquals(day, savedAttendance.getCourseClass().getDay());
        assertEquals(course.getId(), savedAttendance.getCourseClass().getCourse().getId());
        assertTrue(presences.existsByPersonAndCourseClass(savedAttendance.getPerson(), savedAttendance.getCourseClass()));
        assertEquals(PresenceStatus.PRESENT, savedAttendance.getStatus());
        assertEquals(2, presences.findByPersonId(person.getId()).size());
        assertEquals(1, presences.findByCourseClassIdAndCourseId(courseClass.getId(), course.getId()).size());
        assertEquals(attendance.getId(), presences.findByCourseClassIdAndPersonId(courseClass.getId(), person.getId())
                .orElseThrow().getId());
        savedAttendance.setStatus(PresenceStatus.JUSTIFIED);
        entityManager.flush();
        entityManager.clear();
        assertEquals(PresenceStatus.JUSTIFIED, entityManager.find(Presence.class, attendance.getId()).getStatus());
        assertEquals(PresenceStatus.ABSENT, entityManager.find(Presence.class, otherAttendance.getId()).getStatus());
        assertEquals("JUSTIFIED", entityManager.createNativeQuery(
                "select status from presence where id = :id", String.class)
                .setParameter("id", attendance.getId()).getSingleResult());
    }

    @Test
    void databaseRejectsDuplicateAttendanceForPersonAndClass() {
        var day = LocalDate.of(2026, 9, 29);
        var person = PersonTestData.person("98765432100");
        var course = new Course("Informática", null, day, day.plusMonths(1));
        entityManager.persist(person);
        entityManager.persist(course);
        var courseClass = new CourseClass(day, "Manhã", day.atTime(8, 0), day.atTime(10, 0), course);
        entityManager.persist(courseClass);
        presences.saveAndFlush(new Presence(person, courseClass, course, PresenceStatus.PRESENT));
        var otherPerson = PersonTestData.person("12345678901");
        entityManager.persist(otherPerson);
        presences.saveAndFlush(new Presence(otherPerson, courseClass, course, PresenceStatus.ABSENT));
        assertEquals(2, presences.findByCourseClassIdAndCourseId(courseClass.getId(), course.getId()).size());

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> presences.saveAndFlush(new Presence(person, courseClass, course, PresenceStatus.ABSENT)));
    }
}
