package com.example.qualificamaisanalitic.services;

import com.enums.Period;
import com.dtos.CourseDtos.CourseDto;
import com.dtos.CourseDtos.UpdateCourseDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.enums.StatusCourse;
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
import java.time.LocalTime;
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
        service = new CourseService(repository, Mappers.getMapper(CourseMapper.class),
                mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    @Test void crudPublishesChangeOnlyAfterSuccessfulRepositoryOperation() {
        when(repository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var events = new java.util.ArrayList<Object>();
        var contextual = new org.springframework.context.annotation.AnnotationConfigApplicationContext();
        contextual.addApplicationListener(event -> { if (event instanceof org.springframework.context.PayloadApplicationEvent<?> payload) events.add(payload.getPayload()); });
        contextual.registerBean(CourseRepository.class, () -> repository);
        contextual.registerBean(CourseMapper.class, () -> Mappers.getMapper(CourseMapper.class));
        contextual.registerBean(CourseService.class); contextual.refresh();
        try {
            contextual.getBean(CourseService.class).addCourse(new CourseDto("Java",null,START,START, 20));
            assertEquals(1,events.size());
            events.clear();
            doThrow(new IllegalStateException("failed save")).when(repository).save(any());
            assertThrows(IllegalStateException.class, () -> contextual.getBean(CourseService.class)
                .addCourse(new CourseDto("Java",null,START,START, 20)));
            assertTrue(events.isEmpty());
        } finally { contextual.close(); }
    }

    @Test
    void updatesCapacityAndPreservesItWhenOmittedFromLaterPatch() {
        var course = new Course("Example course", null, START, START.plusMonths(1), StatusCourse.ACTIVE, 20);
        course.setId(3L);
        when(repository.findByid(3L)).thenReturn(Optional.of(course));

        service.updateCourse(new UpdateCourseDto(3L, null, null, null, null, 30));
        assertEquals(30, course.getMaximumStudentsCount());

        service.updateCourse(new UpdateCourseDto(3L, "Updated course", null, null, null, null));
        assertEquals(30, course.getMaximumStudentsCount());
        assertEquals("Updated course", course.getName());
    }

    @Test
    void createsCourseWithItsDatesAndDescription() {
        var saved = new Course("Informática", "Introdução", START, START.plusMonths(1), StatusCourse.ACTIVE, 20);
        saved.setId(3L);
        when(repository.save(any(Course.class))).thenReturn(saved);
        var result = service.addCourse(new CourseDto("Informática", "Introdução", START, START.plusMonths(1), 20));
        var capture = ArgumentCaptor.forClass(Course.class);
        verify(repository).save(capture.capture());
        var course = capture.getValue();
        assertNull(course.getId());
        assertEquals("Informática", course.getName());
        assertEquals("Introdução", course.getDescription());
        assertEquals(START, course.getStart());
        assertEquals(START.plusMonths(1), course.getFinish());
        assertEquals(new CourseDto(3L, "Informática", "Introdução", START, START.plusMonths(1), null, 20), result);
    }

    @Test
    void partialUpdatePreservesIdentityDatesAndClasses() {
        var course = new Course("Informática", "Descrição", START, START.plusMonths(1), StatusCourse.ACTIVE, 20);
        course.setId(3L);
        var courseClass = new CourseClass(START, Period.MORNING, LocalTime.of(8, 0), LocalTime.of(10, 0), course);
        course.getCourseClass().add(courseClass);
        when(repository.findByid(3L)).thenReturn(Optional.of(course));
        var result = service.updateCourse(new UpdateCourseDto(3L, "Informática básica", null, null, null, null));
        assertEquals("Informática básica", course.getName());
        assertEquals("Descrição", course.getDescription());
        assertEquals(START, course.getStart());
        assertEquals(START.plusMonths(1), course.getFinish());
        assertEquals(3L, course.getId());
        assertEquals(List.of(courseClass), course.getCourseClass());
        assertEquals(new CourseDto(3L, "Informática básica", "Descrição", START, START.plusMonths(1), null, 20), result);
        verify(repository).save(course);
    }

    @Test
    void updatesAllProvidedCourseFields() {
        var course = new Course("Antigo", null, START, START,StatusCourse.ACTIVE, 20);
        when(repository.findByid(3L)).thenReturn(Optional.of(course));
        service.updateCourse(new UpdateCourseDto(3L, "Novo", "Descrição", START.plusDays(1), START.plusMonths(2), null));
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
                () -> service.updateCourse(new UpdateCourseDto(3L, "Novo", null, null, null, null)));
        verify(repository, never()).save(any());
    }

    @Test
    void listsAndFindsCoursesIncludingAbsentName() {
        var course = new Course("Informática", null, START, START,StatusCourse.ACTIVE, 20);
        course.setId(3L);
        var expected = new CourseDto(3L, "Informática", null, START, START, null, 20);
        when(repository.findAll()).thenReturn(List.of(course));
        when(repository.findByName("Informática")).thenReturn(Optional.of(expected));
        when(repository.findByName("Ausente")).thenReturn(Optional.empty());
        assertEquals(List.of(expected), service.getAllCourses());
        assertEquals(expected, service.getCourseByName("Informática"));
        assertThrows(EntityNotFoundException.class, () -> service.getCourseByName("Ausente"));
    }

    @Test
    void cancelsTheRequestedCourseWithoutDeletingIt() {
        var course = new Course("Informática", null, START, START, StatusCourse.ACTIVE, 20);
        course.setId(3L);
        when(repository.findById(3L)).thenReturn(Optional.of(course));
        service.deleteCourse(3L);
        assertEquals(StatusCourse.CANCELED, course.getStatusCourse());
        assertEquals(3L, course.getId());
        verify(repository, never()).delete(any(Course.class));
    }
}
