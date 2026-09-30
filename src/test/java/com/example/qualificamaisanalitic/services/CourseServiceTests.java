package com.example.qualificamaisanalitic.services;

import com.dtos.CourseDtos.AddCourseDto;
import com.dtos.CourseDtos.UpdateCourseDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.mappers.CourseMapper;
import com.repositories.CourseRepository;
import com.services.CourseService;
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
class CourseServiceTests {
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    @Mock private CourseRepository repository;
    private CourseService service;

    @BeforeEach
    void setUp() {
        service = new CourseService(repository, Mappers.getMapper(CourseMapper.class));
    }

    @Test
    void createsCourseWithItsDatesAndDescription() {
        service.addCourse(new AddCourseDto("Informática", "Introdução", START, START.plusMonths(1)));
        var capture = ArgumentCaptor.forClass(Course.class);
        verify(repository).save(capture.capture());
        var course = capture.getValue();
        assertNull(course.getId());
        assertEquals("Informática", course.getName());
        assertEquals("Introdução", course.getDescription());
        assertEquals(START, course.getStart());
        assertEquals(START.plusMonths(1), course.getFinish());
    }

    @Test
    void partialUpdatePreservesIdentityDatesAndClasses() {
        var course = new Course("Informática", "Descrição", START, START.plusMonths(1));
        course.setId(3L);
        var courseClass = new CourseClass(START, "Manhã", START.atTime(8, 0), START.atTime(10, 0), course);
        course.getCourseClass().add(courseClass);
        when(repository.findByid(3L)).thenReturn(Optional.of(course));
        service.updateCourse(new UpdateCourseDto(3L, "Informática básica", null, null, null));
        assertEquals("Informática básica", course.getName());
        assertEquals("Descrição", course.getDescription());
        assertEquals(START, course.getStart());
        assertEquals(START.plusMonths(1), course.getFinish());
        assertEquals(3L, course.getId());
        assertEquals(List.of(courseClass), course.getCourseClass());
        verify(repository).save(course);
    }

    @Test
    void updatesAllProvidedCourseFields() {
        var course = new Course("Antigo", null, START, START);
        when(repository.findByid(3L)).thenReturn(Optional.of(course));
        service.updateCourse(new UpdateCourseDto(3L, "Novo", "Descrição", START.plusDays(1), START.plusMonths(2)));
        assertEquals("Novo", course.getName());
        assertEquals("Descrição", course.getDescription());
        assertEquals(START.plusDays(1), course.getStart());
        assertEquals(START.plusMonths(2), course.getFinish());
        verify(repository).save(course);
    }

    @Test
    void refusesUpdateWhenCourseDoesNotExist() {
        when(repository.findByid(3L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.updateCourse(new UpdateCourseDto(3L, "Novo", null, null, null)));
        verify(repository, never()).save(any());
    }

    @Test
    void listsAndFindsCoursesIncludingAbsentName() {
        var course = new Course("Informática", null, START, START);
        when(repository.findAll()).thenReturn(List.of(course));
        when(repository.findByName("Informática")).thenReturn(Optional.of(course));
        when(repository.findByName("Ausente")).thenReturn(Optional.empty());
        assertEquals(List.of(course), service.getAllCourses());
        assertSame(course, service.getCourseByName("Informática"));
        assertThrows(EntityNotFoundException.class, () -> service.getCourseByName("Ausente"));
    }

    @Test
    void deletesTheRequestedCourse() {
        var course = new Course();
        when(repository.getById(Long.valueOf(3))).thenReturn(course);
        service.deleteCourse(3L);
        verify(repository).delete(course);
    }
}
