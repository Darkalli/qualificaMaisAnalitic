package com.example.qualificamaisanalitic.services;

import com.dtos.presenceDtos.AddPresenceDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Person;
import com.entities.Presence;
import com.entities.Register;
import com.enums.Period;
import com.enums.PresenceStatus;
import com.enums.StatusRegister;
import com.exceptions.ConflictException;
import com.repositories.CourseClassRepository;
import com.repositories.CourseRepository;
import com.repositories.PersonRepository;
import com.repositories.PresenceRepository;
import com.repositories.RegisterRepository;
import com.services.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitlistedPresenceServiceTests {
    @Mock private PresenceRepository presences;
    @Mock private PersonRepository people;
    @Mock private CourseClassRepository classes;
    @Mock private RegisterRepository registers;
    @Mock private CourseRepository courses;

    private PresenceService service;
    private Register registration;

    @BeforeEach
    void setUp() {
        service = new PresenceService(presences, people, classes, registers, courses);
        LocalDate day = LocalDate.of(2026, 11, 25);
        Person person = new Person();
        person.setId(7L);
        person.setCpf("01234567890");
        Course course = new Course();
        course.setId(5L);
        CourseClass lesson = new CourseClass(day, Period.MORNING,
                LocalTime.of(9, 0), LocalTime.of(11, 0), course);
        lesson.setId(3L);
        registration = new Register(person, course, day.minusDays(1).atTime(8, 0),
                StatusRegister.WAITLISTED);

        when(classes.findCourseIdById(3L)).thenReturn(Optional.of(5L));
        when(courses.findByIdForRegistration(5L)).thenReturn(Optional.of(course));
        when(people.findByIdForUpdate(7L)).thenReturn(Optional.of(person));
        when(classes.findById(3L)).thenReturn(Optional.of(lesson));
        when(registers.findByPerson_CpfAndCourseOfInterest_Id(person.getCpf(), 5L))
                .thenReturn(Optional.of(registration));
    }

    @ParameterizedTest
    @EnumSource(PresenceStatus.class)
    void rejectsEveryAttendanceStatusWhileRegistrationIsWaitlisted(PresenceStatus status) {
        assertThrows(ConflictException.class,
                () -> service.addPresence(new AddPresenceDto(7L, 3L, status)));

        assertEquals(StatusRegister.WAITLISTED, registration.getStatus());
        verify(presences, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(PresenceStatus.class)
    void allowsAttendanceAfterReserveBecomesActive(PresenceStatus status) {
        registration.setStatus(StatusRegister.ACTIVE);
        when(presences.save(any(Presence.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Presence saved = service.addPresence(new AddPresenceDto(7L, 3L, status));

        assertEquals(status, saved.getStatus());
        assertSame(registration.getPerson(), saved.getPerson());
        assertSame(registration.getCourseOfInterest(), saved.getCourse());
        verify(presences).save(saved);
    }
}
