package com.example.qualificamaisanalitic.services;

import com.dtos.CourseDtos.AddCourseDto;
import com.dtos.CourseDtos.UpdateCourseDto;
import com.entities.Course;
import com.enums.Period;
import com.enums.StatusCourse;
import com.services.CourseService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoursePeriodPersistenceTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    @Autowired private CourseService courses;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(Period.class)
    void storesTheSelectedPeriodAsTextAndReloadsIt(Period period) {
        var created = courses.addCourse(new AddCourseDto("Curso " + period.name(), null,
                DAY, DAY.plusMonths(1), period));
        Long id = created.getId();
        entityManager.flush();
        entityManager.clear();

        var saved = entityManager.find(Course.class, id);
        assertEquals(period, saved.getPeriod());
        assertEquals(StatusCourse.ACTIVE, saved.getStatusCourse());
        assertEquals(period.name(), jdbc.queryForObject("select period from course where id = ?", String.class, id));
        assertEquals(period, courses.getAllCourses().stream()
                .filter(course -> course.getId().equals(id)).findFirst().orElseThrow().getPeriod());
    }

    @Test
    void updatesPeriodAndPreservesItWhenAnotherFieldIsChanged() {
        var created = courses.addCourse(new AddCourseDto("Curso de teste", "Descrição", DAY,
                DAY.plusMonths(1), Period.MORNING));
        Long id = created.getId();
        courses.updateCourse(new UpdateCourseDto(id, null, null, null, null, Period.NIGHT));
        entityManager.flush();
        entityManager.clear();
        assertEquals(Period.NIGHT, entityManager.find(Course.class, id).getPeriod());

        courses.updateCourse(new UpdateCourseDto(id, "Curso atualizado", null, null, null));
        entityManager.flush();
        entityManager.clear();
        var saved = entityManager.find(Course.class, id);
        assertEquals(Period.NIGHT, saved.getPeriod());
        assertEquals("Curso atualizado", saved.getName());
        assertEquals("Descrição", saved.getDescription());
        assertEquals(DAY, saved.getStart());
    }

    @Test
    void preservesCompatibilityWithRequestsWithoutPeriod() {
        var created = courses.addCourse(new AddCourseDto("Curso sem período", null, DAY, DAY));
        Long id = created.getId();
        entityManager.flush();
        entityManager.clear();
        assertNull(entityManager.find(Course.class, id).getPeriod());
    }
}
