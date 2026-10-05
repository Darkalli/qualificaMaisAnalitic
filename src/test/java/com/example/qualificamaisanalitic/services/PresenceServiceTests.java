package com.example.qualificamaisanalitic.services;

import com.dtos.presenceDtos.*;
import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Presence;
import com.entities.Register;
import com.enums.StatusRegister;
import com.exceptions.ConflictException;
import com.enums.PresenceStatus;
import com.repositories.CourseClassRepository;
import com.repositories.PersonRepository;
import com.repositories.PresenceRepository;
import com.repositories.RegisterRepository;
import com.repositories.CourseRepository;
import com.services.PresenceService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class PresenceServiceTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    @Mock private PresenceRepository presences;
    @Mock private PersonRepository people;
    @Mock private CourseClassRepository classes;
    @Mock private RegisterRepository registers;
    @Mock private CourseRepository courses;
    private PresenceService service;

    @BeforeEach
    void setUp() {
        service = new PresenceService(presences, people, classes, registers, courses);
        lenient().when(classes.findCourseIdById(3L)).thenReturn(Optional.of(5L));
        lenient().when(courses.findByIdForRegistration(5L)).thenReturn(Optional.of(new Course()));
    }

    @Test
    void createsAttendanceForExistingPersonAndClassUsingItsCourse() {
        var person = ServiceTestData.person();
        var course = new Course();
        var courseClass = courseClass(course);
        when(people.findByIdForUpdate(7L)).thenReturn(Optional.of(person));
        when(classes.findById(3L)).thenReturn(Optional.of(courseClass));
        when(registers.findByPerson_CpfAndCourseOfInterest_Id(person.getCpf(), course.getId()))
                .thenReturn(Optional.of(new Register(person, course, DAY, StatusRegister.ACTIVE)));
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
        when(people.findByIdForUpdate(7L)).thenReturn(Optional.of(person));
        when(classes.findById(3L)).thenReturn(Optional.of(courseClass));
        when(registers.findByPerson_CpfAndCourseOfInterest_Id(person.getCpf(), courseClass.getCourse().getId()))
                .thenReturn(Optional.of(new Register(person, courseClass.getCourse(), DAY, StatusRegister.ACTIVE)));
        when(presences.existsByPersonAndCourseClass(person, courseClass)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.addPresence(new AddPresenceDto(7L, 3L, PresenceStatus.ABSENT)));

        verify(presences, never()).save(any());
    }

    @Test
    void rejectsAttendanceWithoutRegistrationInTheClassCourse() {
        var person = ServiceTestData.person();
        var course = new Course();
        when(people.findByIdForUpdate(7L)).thenReturn(Optional.of(person));
        when(classes.findById(3L)).thenReturn(Optional.of(courseClass(course)));
        when(registers.findByPerson_CpfAndCourseOfInterest_Id(person.getCpf(), course.getId()))
                .thenReturn(Optional.empty());

        var error = assertThrows(ConflictException.class,
                () -> service.addPresence(new AddPresenceDto(7L, 3L, PresenceStatus.PRESENT)));

        assertEquals("A pessoa não possui inscrição no curso desta aula.", error.getMessage());
        verifyNoInteractions(presences);
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
        when(people.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.addPresence(new AddPresenceDto(7L, 3L, PresenceStatus.PRESENT)));
        verifyNoInteractions(presences, registers);
    }

    @Test
    void rejectsUnknownClassWithoutSaving() {
        when(classes.findCourseIdById(3L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.addPresence(new AddPresenceDto(7L, 3L, PresenceStatus.PRESENT)));
        verifyNoInteractions(presences);
    }

    @Test
    void rejectsAttendanceForCanceledRegistrationWithoutSaving() {
        var person = ServiceTestData.person();
        var course = new Course();
        var courseClass = courseClass(course);
        var registration = new Register(person, course, DAY, StatusRegister.CANCELED);
        when(people.findByIdForUpdate(7L)).thenReturn(Optional.of(person));
        when(classes.findById(3L)).thenReturn(Optional.of(courseClass));
        when(registers.findByPerson_CpfAndCourseOfInterest_Id(person.getCpf(), course.getId()))
                .thenReturn(Optional.of(registration));

        var error = assertThrows(ConflictException.class,
                () -> service.addPresence(new AddPresenceDto(7L, 3L, PresenceStatus.PRESENT)));

        assertEquals("A pessoa possui inscrição cancelada no curso desta aula.", error.getMessage());
        assertEquals(StatusRegister.CANCELED, registration.getStatus());
        verifyNoInteractions(presences);
    }

    private CourseClass courseClass(Course course) {
        course.setId(5L);
        return new CourseClass(DAY, "Manhã", LocalTime.of(8, 0), LocalTime.of(10, 0), course);
    }
}
