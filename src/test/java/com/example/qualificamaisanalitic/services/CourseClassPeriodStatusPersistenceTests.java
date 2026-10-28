package com.example.qualificamaisanalitic.services;

import com.dtos.CourseDtos.AddCourseDto;
import com.dtos.courseClassesDtos.AddCourseClassDto;
import com.dtos.courseClassesDtos.AddCourseClassInBatchDto;
import com.dtos.courseClassesDtos.UpdateCourseClassDto;
import com.entities.CourseClass;
import com.enums.Period;
import com.enums.StatusClass;
import com.services.CourseClassService;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseClassPeriodStatusPersistenceTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 28);
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime FINISH = LocalTime.of(10, 0);
    @Autowired private CourseService courses;
    @Autowired private CourseClassService classes;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(Period.class)
    void storesPeriodAsTextAndReloadsIt(Period period) {
        Long courseId = newCourse();
        var saved = classes.addCourseClass(new AddCourseClassDto(DAY, period, START, FINISH, courseId));
        Long id = saved.getId();
        em.flush();
        em.clear();
        assertEquals(period.name(), jdbc.queryForObject("select session from course_class where id = ?", String.class, id));
        assertEquals(period, em.find(CourseClass.class, id).getSession());
        assertEquals(StatusClass.ACTIVE, em.find(CourseClass.class, id).getStatusClass());
    }

    @ParameterizedTest
    @EnumSource(StatusClass.class)
    void storesSelectedStatusInSingleAndBatchCreation(StatusClass status) {
        Long courseId = newCourse();
        var single = classes.addCourseClass(new AddCourseClassDto(DAY, Period.MORNING, START, FINISH, courseId, status));
        var batch = classes.addCourseClassInBatch(new AddCourseClassInBatchDto(
                new ArrayList<>(List.of(DAY.plusDays(1), DAY.plusDays(2))), Period.NIGHT, START, FINISH, courseId, status));
        Long singleId = single.getId();
        List<Long> batchIds = batch.stream().map(CourseClass::getId).toList();
        em.flush();
        em.clear();
        assertEquals(status, em.find(CourseClass.class, singleId).getStatusClass());
        for (Long id : batchIds) {
            assertEquals(status, em.find(CourseClass.class, id).getStatusClass());
            assertEquals(Period.NIGHT, em.find(CourseClass.class, id).getSession());
            assertEquals(status.name(), jdbc.queryForObject("select status_class from course_class where id = ?", String.class, id));
        }
    }

    @Test
    void partialUpdatesPersistSelectedEnumsWithoutErasingOmittedValues() {
        var saved = classes.addCourseClass(new AddCourseClassDto(DAY, Period.MORNING, START, FINISH, newCourse()));
        Long id = saved.getId();
        classes.updateCourseClass(new UpdateCourseClassDto(id, null, Period.AFTERNOON, null, null, null, StatusClass.POSTPONED));
        em.flush();
        em.clear();
        classes.updateCourseClass(new UpdateCourseClassDto(id, null, null, null, null, null, null));
        em.flush();
        em.clear();
        var reloaded = em.find(CourseClass.class, id);
        assertEquals(Period.AFTERNOON, reloaded.getSession());
        assertEquals(StatusClass.POSTPONED, reloaded.getStatusClass());
        assertEquals(DAY, reloaded.getDay());
        assertEquals(START, reloaded.getStart());
        assertEquals(FINISH, reloaded.getFinish());
    }

    private Long newCourse() {
        return courses.addCourse(new AddCourseDto("Curso de teste", null, DAY, DAY.plusMonths(1))).getId();
    }
}
