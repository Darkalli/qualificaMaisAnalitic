package com.example.qualificamaisanalitic.services;

import com.dtos.presenceDtos.*;
import com.entities.Course;
import com.entities.Presence;
import com.enums.PresenceStatus;
import com.repositories.CourseRepository;
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
    @Mock private CourseRepository courses;
    private PresenceService service;

    @BeforeEach
    void setUp() {
        service = new PresenceService(presences, people, courses);
    }

    @Test
    void createsAttendanceForExistingPersonAndCourse() {
        var person = ServiceTestData.person();
        var course = new Course();
        when(people.getById(Long.valueOf(7))).thenReturn(person);
        when(courses.getById(Long.valueOf(3))).thenReturn(course);
        service.addPresence(new AddPresenceDto(7L, DAY, 3L, PresenceStatus.PRESENT));
        var capture = ArgumentCaptor.forClass(Presence.class);
        verify(presences).save(capture.capture());
        var presence = capture.getValue();
        assertNull(presence.getId());
        assertSame(person, presence.getPerson());
        assertSame(course, presence.getCourse());
        assertEquals(DAY, presence.getDate());
        assertEquals(PresenceStatus.PRESENT, presence.getStatus());
    }

    @Test
    void updatesOnlySelectedCourseAttendanceOnTheSameDay() {
        var person = ServiceTestData.person();
        var first = new Presence(person, DAY, new Course(), PresenceStatus.PRESENT);
        var second = new Presence(person, DAY, new Course(), PresenceStatus.ABSENT);
        when(presences.findByDateAndPersonIdAndCourseId(DAY, 7L, 4L)).thenReturn(Optional.of(second));
        service.updatePresence(new PresenceUpdateDto(7L, DAY, PresenceStatus.JUSTIFIED, 4L));
        assertEquals(PresenceStatus.JUSTIFIED, second.getStatus());
        assertEquals(PresenceStatus.PRESENT, first.getStatus());
        assertEquals(DAY, second.getDate());
        assertSame(person, second.getPerson());
        verify(presences).save(second);
        verify(presences, never()).save(first);
    }

    @Test
    void reportsMissingAttendanceWithoutSaving() {
        when(presences.findByDateAndPersonIdAndCourseId(DAY, 7L, 3L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.updatePresence(new PresenceUpdateDto(7L, DAY, PresenceStatus.PRESENT, 3L)));
        verify(presences, never()).save(any());
    }

    @Test
    void filtersAttendanceByPersonAndByDateAndCourse() {
        var result = List.of(new Presence(ServiceTestData.person(), DAY, new Course(), PresenceStatus.PRESENT));
        when(presences.findByPersonId(7L)).thenReturn(result);
        when(presences.findByDateAndCourseId(DAY, 3L)).thenReturn(result);
        assertEquals(result, service.getPresenceByPerson(new PresenceByPersonDto(7L)));
        assertEquals(result, service.getPresenceByDateAndCourse(new PresenceByDayAndCourseDto(3L, DAY)));
    }
}
