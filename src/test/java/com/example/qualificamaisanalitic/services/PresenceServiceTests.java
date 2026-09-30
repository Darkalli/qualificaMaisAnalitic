package com.example.qualificamaisanalitic.services;

import com.dtos.presenceDtos.*;
import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Presence;
import com.enums.PresenceStatus;
import com.repositories.CourseClassRepository;
import com.repositories.PersonRepository;
import com.repositories.PresenceRepository;
import com.services.PresenceService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    @Mock private PresenceRepository presences;
    @Mock private PersonRepository people;
    @Mock private CourseClassRepository classes;
    private PresenceService service;

    @BeforeEach
    void setUp() {
        service = new PresenceService(presences, people, classes);
    }

    @Test
    void createsAttendanceForExistingPersonAndClassUsingItsCourse() {
        var person = ServiceTestData.person();
        var course = new Course();
        var courseClass = courseClass(course);
        when(people.findById(7L)).thenReturn(Optional.of(person));
        when(classes.findById(3L)).thenReturn(Optional.of(courseClass));
        service.addPresence(new AddPresenceDto(7L, 3L, PresenceStatus.PRESENT));
        var capture = ArgumentCaptor.forClass(Presence.class);
        verify(presences).save(capture.capture());
        var presence = capture.getValue();
        assertNull(presence.getId());
        assertSame(person, presence.getPerson());
        assertSame(course, presence.getCourse());
        assertSame(courseClass, presence.getCourseClass());
        assertEquals(DAY, presence.getCourseClass().getDay());
        assertEquals(PresenceStatus.PRESENT, presence.getStatus());
        verify(presences).existsByPersonAndCourseClass(person, courseClass);
    }

    @Test
    void rejectsDuplicateAttendanceForTheSamePersonAndClassWithoutSaving() {
        var person = ServiceTestData.person();
        var courseClass = courseClass(new Course());
        when(people.findById(7L)).thenReturn(Optional.of(person));
        when(classes.findById(3L)).thenReturn(Optional.of(courseClass));
        when(presences.existsByPersonAndCourseClass(person, courseClass)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.addPresence(new AddPresenceDto(7L, 3L, PresenceStatus.ABSENT)));

        verify(presences, never()).save(any());
    }

    @Test
    void updatesStatusWithoutChangingPersonClassOrCourse() {
        var person = ServiceTestData.person();
        var course = new Course();
        var courseClass = courseClass(course);
        var second = new Presence(person, courseClass, course, PresenceStatus.ABSENT);
        when(presences.findByCourseClassIdAndPersonId(3L, 7L)).thenReturn(Optional.of(second));
        service.updatePresence(new PresenceUpdateDto(7L, 3L, PresenceStatus.JUSTIFIED));
        assertEquals(PresenceStatus.JUSTIFIED, second.getStatus());
        assertSame(courseClass, second.getCourseClass());
        assertSame(course, second.getCourse());
        assertSame(person, second.getPerson());
        verify(presences).save(second);
    }

    @Test
    void reportsMissingAttendanceWithoutSaving() {
        when(presences.findByCourseClassIdAndPersonId(3L, 7L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.updatePresence(new PresenceUpdateDto(7L, 3L, PresenceStatus.PRESENT)));
        verify(presences, never()).save(any());
    }

    @Test
    void filtersAttendanceByPersonAndByClassAndCourse() {
        var course = new Course();
        var result = List.of(new Presence(ServiceTestData.person(), courseClass(course), course, PresenceStatus.PRESENT));
        when(presences.findByPersonId(7L)).thenReturn(result);
        when(presences.findByCourseClassIdAndCourseId(4L, 3L)).thenReturn(result);
        assertEquals(result, service.getPresenceByPerson(7L));
        assertEquals(result, service.getPresenceByDateAndCourse(new PresenceByDayAndCourseDto(3L, 4L)));
    }

    @Test
    void rejectsUnknownPersonWithoutSaving() {
        when(people.findById(7L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.addPresence(new AddPresenceDto(7L, 3L, PresenceStatus.PRESENT)));
        verifyNoInteractions(classes, presences);
    }

    @Test
    void rejectsUnknownClassWithoutSaving() {
        when(people.findById(7L)).thenReturn(Optional.of(ServiceTestData.person()));
        when(classes.findById(3L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.addPresence(new AddPresenceDto(7L, 3L, PresenceStatus.PRESENT)));
        verifyNoInteractions(presences);
    }

    private CourseClass courseClass(Course course) {
        return new CourseClass(DAY, "Manhã", DAY.atTime(8, 0), DAY.atTime(10, 0), course);
    }
}
