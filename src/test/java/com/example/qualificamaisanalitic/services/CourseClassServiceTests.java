package com.example.qualificamaisanalitic.services;

import com.dtos.courseClassesDtos.AddCourseClassDto;
import com.dtos.courseClassesDtos.UpdateCourseClassDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.mappers.CourseClassMapper;
import com.repositories.CourseClassRepository;
import com.repositories.CourseRepository;
import com.repositories.PersonRepository;
import com.repositories.RegisterRepository;
import com.repositories.PresenceRepository;
import com.services.CourseClassService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseClassServiceTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    @Mock private CourseClassRepository classes;
    @Mock private CourseRepository courses;
    @Mock private PersonRepository people;
    @Mock private RegisterRepository registers;
    @Mock private PresenceRepository presences;
    private CourseClassService service;

    @BeforeEach
    void setUp() {
        service = new CourseClassService(classes, courses, Mappers.getMapper(CourseClassMapper.class), people, registers, presences);
        lenient().when(classes.findCourseIdById(4L)).thenReturn(Optional.of(3L));
    }

    @Test
    void createsClassWithCourseDaySessionAndTimes() {
        var course = course(3L);
        course.setId(3L);
        when(courses.findByid(3L)).thenReturn(Optional.of(course));
        service.addCourseClass(new AddCourseClassDto(DAY, "Manhã", LocalTime.of(8, 0), LocalTime.of(10, 0), course.getId()));
        var capture = ArgumentCaptor.forClass(CourseClass.class);
        verify(classes).save(capture.capture());
        var saved = capture.getValue();
        assertNull(saved.getId());
        assertSame(course, saved.getCourse());
        assertEquals(DAY, saved.getDay());
        assertEquals("Manhã", saved.getSession());
        assertEquals(LocalTime.of(8, 0), saved.getStart());
        assertEquals(LocalTime.of(10, 0), saved.getFinish());
    }

    @Test
    void partialUpdateChangesTimesWithoutErasingDayCourseOrId() {
        var course = course(3L);
        var saved = new CourseClass(DAY, "Manhã", LocalTime.of(8, 0), LocalTime.of(10, 0), course);
        saved.setId(4L);
        when(classes.findById(4L)).thenReturn(Optional.of(saved));
        service.updateCourseClass(new UpdateCourseClassDto(4L, null, "Tarde", LocalTime.of(14, 0), LocalTime.of(16, 0), null));
        assertEquals(4L, saved.getId());
        assertSame(course, saved.getCourse());
        assertEquals(DAY, saved.getDay());
        assertEquals("Tarde", saved.getSession());
        assertEquals(LocalTime.of(14, 0), saved.getStart());
        assertEquals(LocalTime.of(16, 0), saved.getFinish());
        verify(classes).save(saved);
    }

    @Test
    void changesCourseAndDayWhenProvided() {
        var saved = new CourseClass(DAY, "Manhã", LocalTime.of(8, 0), LocalTime.of(10, 0), course(3L));
        var replacement = course(5L);
        when(classes.findById(4L)).thenReturn(Optional.of(saved));
        service.updateCourseClass(new UpdateCourseClassDto(4L, DAY.plusDays(1), null, null, null, replacement));
        assertSame(replacement, saved.getCourse());
        assertEquals(DAY.plusDays(1), saved.getDay());
        assertEquals("Manhã", saved.getSession());
        verify(classes).save(saved);
    }

    @Test
    void refusesUpdateOrDeletionOfUnknownClass() {
        when(classes.findCourseIdById(4L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.updateCourseClass(new UpdateCourseClassDto(4L, null, null, null, null, null)));
        assertThrows(EntityNotFoundException.class, () -> service.deleteCourseClass(4L));
        verify(classes, never()).save(any());
        verify(classes, never()).delete(any());
    }

    @Test
    void listsOnlyClassesOfRequestedCourseAndDeletesRequestedClass() {
        var course = course(3L);
        var courseClass = new CourseClass(DAY, "Manhã", LocalTime.of(8, 0), LocalTime.of(10, 0), course);
        when(courses.findByid(3L)).thenReturn(Optional.of(course));
        when(classes.findByCourse(course)).thenReturn(List.of(courseClass));
        when(classes.findById(4L)).thenReturn(Optional.of(courseClass));
        assertEquals(List.of(courseClass), service.allClassesByCourseId(3L));
        service.deleteCourseClass(4L);
        assertEquals(com.enums.StatusClass.CANCELED, courseClass.getStatusClass());
        verify(classes).save(courseClass);
        verify(classes, never()).delete(any());
    }

    @Test
    void refusesClassSearchForUnknownCourse() {
        when(courses.findByid(3L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.allClassesByCourseId(3L));
        verifyNoInteractions(classes);
    }

    @Test
    void refusesSecondClassOnSameCourseAndDayEvenWithDifferentSessionAndTimes() {
        var course = course(3L);
        course.setId(3L);
        when(courses.findByid(3L)).thenReturn(Optional.of(course));
        when(classes.existsByCourseAndDay(course, DAY)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.addCourseClass(
                new AddCourseClassDto(DAY, "Tarde", LocalTime.of(14, 0), LocalTime.of(16, 0), course.getId())));

        verify(classes, never()).save(any());
    }

    @Test
    void refusesMovingClassToAnOccupiedCourseAndDayWithoutSaving() {
        var course = course(3L);
        course.setId(3L);
        var saved = new CourseClass(DAY.minusDays(1), "Manhã", LocalTime.of(8, 0), LocalTime.of(10, 0), course);
        saved.setId(4L);
        when(classes.findById(4L)).thenReturn(Optional.of(saved));
        when(classes.existsByCourseAndDayAndIdNot(course, DAY, 4L)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.updateCourseClass(
                new UpdateCourseClassDto(4L, DAY, "Tarde", LocalTime.of(14, 0), LocalTime.of(16, 0), course)));

        verify(classes, never()).save(any());
        assertEquals(DAY.minusDays(1), saved.getDay());
        assertEquals("Manhã", saved.getSession());
    }
    private Course course(long id) {
        var course = new Course();
        course.setId(id);
        lenient().when(courses.findByIdForUpdate(id)).thenReturn(Optional.of(course));
        return course;
    }
}
