package com.example.qualificamaisanalitic.services;

import com.dtos.courseClassesDtos.AddCourseClassDto;
import com.dtos.courseClassesDtos.UpdateCourseClassDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.mappers.CourseClassMapper;
import com.repositories.CourseClassRepository;
import com.repositories.CourseRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseClassServiceTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    @Mock private CourseClassRepository classes;
    @Mock private CourseRepository courses;
    private CourseClassService service;

    @BeforeEach
    void setUp() {
        service = new CourseClassService(classes, courses, Mappers.getMapper(CourseClassMapper.class));
    }

    @Test
    void createsClassWithCourseDaySessionAndTimes() {
        var course = new Course();
        course.setId(3L);
        service.addCourseClass(new AddCourseClassDto(DAY, "Manhã", DAY.atTime(8, 0), DAY.atTime(10, 0), course));
        var capture = ArgumentCaptor.forClass(CourseClass.class);
        verify(classes).save(capture.capture());
        var saved = capture.getValue();
        assertNull(saved.getId());
        assertSame(course, saved.getCourse());
        assertEquals(DAY, saved.getDay());
        assertEquals("Manhã", saved.getSession());
        assertEquals(DAY.atTime(8, 0), saved.getStart());
        assertEquals(DAY.atTime(10, 0), saved.getFinish());
    }

    @Test
    void partialUpdateChangesTimesWithoutErasingDayCourseOrId() {
        var course = new Course();
        var saved = new CourseClass(DAY, "Manhã", DAY.atTime(8, 0), DAY.atTime(10, 0), course);
        saved.setId(4L);
        when(classes.findById(4L)).thenReturn(Optional.of(saved));
        service.updateCourseClass(new UpdateCourseClassDto(4L, null, "Tarde", DAY.atTime(14, 0), DAY.atTime(16, 0), null));
        assertEquals(4L, saved.getId());
        assertSame(course, saved.getCourse());
        assertEquals(DAY, saved.getDay());
        assertEquals("Tarde", saved.getSession());
        assertEquals(DAY.atTime(14, 0), saved.getStart());
        assertEquals(DAY.atTime(16, 0), saved.getFinish());
        verify(classes).save(saved);
    }

    @Test
    void changesCourseAndDayWhenProvided() {
        var saved = new CourseClass(DAY, "Manhã", DAY.atTime(8, 0), DAY.atTime(10, 0), new Course());
        var replacement = new Course();
        when(classes.findById(4L)).thenReturn(Optional.of(saved));
        service.updateCourseClass(new UpdateCourseClassDto(4L, DAY.plusDays(1), null, null, null, replacement));
        assertSame(replacement, saved.getCourse());
        assertEquals(DAY.plusDays(1), saved.getDay());
        assertEquals("Manhã", saved.getSession());
        verify(classes).save(saved);
    }

    @Test
    void refusesUpdateOrDeletionOfUnknownClass() {
        when(classes.findById(4L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.updateCourseClass(new UpdateCourseClassDto(4L, null, null, null, null, null)));
        assertThrows(EntityNotFoundException.class, () -> service.deleteCourseClass(4L));
        verify(classes, never()).save(any());
        verify(classes, never()).delete(any());
    }

    @Test
    void listsOnlyClassesOfRequestedCourseAndDeletesRequestedClass() {
        var course = new Course();
        var courseClass = new CourseClass();
        when(courses.findByid(3L)).thenReturn(Optional.of(course));
        when(classes.findByCourse(course)).thenReturn(List.of(courseClass));
        when(classes.findById(4L)).thenReturn(Optional.of(courseClass));
        assertEquals(List.of(courseClass), service.allClassesByCourseId(3L));
        service.deleteCourseClass(4L);
        verify(classes).delete(courseClass);
    }

    @Test
    void refusesClassSearchForUnknownCourse() {
        when(courses.findByid(3L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.allClassesByCourseId(3L));
        verifyNoInteractions(classes);
    }
}
