package com.example.qualificamaisanalitic.services;

import com.dtos.CourseDtos.AddCourseDto;
import com.dtos.courseClassesDtos.AddCourseClassDto;
import com.dtos.courseClassesDtos.UpdateCourseClassDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.services.CourseClassService;
import com.services.CourseService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Verifica a validação do serviço; a V1 também impõe a regra diária no banco. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseClassDailyRuleTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    @Autowired private CourseService courses;
    @Autowired private CourseClassService classes;
    @Autowired private EntityManager entityManager;

    @Test
    void rejectsDuplicatesButAllowsDifferentCoursesAndDays() {
        var first = course("Curso A");
        var second = course("Curso B");
        addClass(first, DAY);
        flushAndClear();

        assertThrows(IllegalArgumentException.class, () -> classes.addCourseClass(
                new AddCourseClassDto(DAY, "Tarde", DAY.atTime(14, 0), DAY.atTime(16, 0), first)));
        addClass(second, DAY);
        addClass(first, DAY.plusDays(1));
        flushAndClear();

        assertEquals(2, classes.allClassesByCourseId(first.getId()).size());
        assertEquals(1, classes.allClassesByCourseId(second.getId()).size());
    }

    @Test
    void resendingOwnCourseAndDayDoesNotConflictWithTheClassBeingUpdated() {
        var course = course("Curso diário");
        var original = addClass(course, DAY);
        flushAndClear();

        classes.updateCourseClass(new UpdateCourseClassDto(original.getId(), DAY, "Tarde",
                DAY.atTime(14, 0), DAY.atTime(16, 0), course));
        flushAndClear();

        var saved = entityManager.find(CourseClass.class, original.getId());
        assertEquals("Tarde", saved.getSession());
        assertEquals(DAY.atTime(14, 0), saved.getStart());
        assertEquals(1, classes.allClassesByCourseId(course.getId()).size());
    }

    @Test
    void changingOnlyDayUsesExistingCourseAndPreservesOmittedFields() {
        var course = course("Curso diário");
        var original = addClass(course, DAY);
        flushAndClear();
        var tomorrow = DAY.plusDays(1);

        classes.updateCourseClass(new UpdateCourseClassDto(original.getId(), tomorrow, null,
                tomorrow.atTime(8, 0), tomorrow.atTime(10, 0), null));
        flushAndClear();

        var saved = entityManager.find(CourseClass.class, original.getId());
        assertEquals(tomorrow, saved.getDay());
        assertEquals(course.getId(), saved.getCourse().getId());
        assertEquals("Manhã", saved.getSession());
    }

    @Test
    void changingOnlyCourseCannotBypassDailyUniqueness() {
        var first = course("Curso A");
        var second = course("Curso B");
        var original = addClass(first, DAY);
        addClass(second, DAY);
        flushAndClear();

        assertThrows(IllegalArgumentException.class, () -> classes.updateCourseClass(
                new UpdateCourseClassDto(original.getId(), null, "Não salvar", null, null, second)));
        flushAndClear();

        var saved = entityManager.find(CourseClass.class, original.getId());
        assertEquals(first.getId(), saved.getCourse().getId());
        assertEquals("Manhã", saved.getSession());
    }

    @Test
    void changingOnlyDayCannotBypassDailyUniqueness() {
        var course = course("Curso diário");
        var original = addClass(course, DAY);
        addClass(course, DAY.plusDays(1));
        flushAndClear();

        assertThrows(IllegalArgumentException.class, () -> classes.updateCourseClass(
                new UpdateCourseClassDto(original.getId(), DAY.plusDays(1), "Não salvar", null, null, null)));
        flushAndClear();

        var saved = entityManager.find(CourseClass.class, original.getId());
        assertEquals(DAY, saved.getDay());
        assertEquals("Manhã", saved.getSession());
    }

    @Test
    void changingOnlyCourseToAnAvailableSlotPreservesDayAndTimes() {
        var first = course("Curso A");
        var second = course("Curso B");
        var original = addClass(first, DAY);
        flushAndClear();

        classes.updateCourseClass(new UpdateCourseClassDto(original.getId(), null, null, null, null, second));
        flushAndClear();

        var saved = entityManager.find(CourseClass.class, original.getId());
        assertEquals(second.getId(), saved.getCourse().getId());
        assertEquals(DAY, saved.getDay());
        assertEquals(DAY.atTime(8, 0), saved.getStart());
        assertEquals(DAY.atTime(10, 0), saved.getFinish());
    }

    private Course course(String name) {
        return courses.addCourse(new AddCourseDto(name, null, DAY, DAY.plusMonths(1)));
    }

    private CourseClass addClass(Course course, LocalDate day) {
        return classes.addCourseClass(new AddCourseClassDto(day, "Manhã", day.atTime(8, 0), day.atTime(10, 0), course));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
