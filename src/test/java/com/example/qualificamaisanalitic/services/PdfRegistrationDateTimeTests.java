package com.example.qualificamaisanalitic.services;

import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Person;
import com.entities.Presence;
import com.entities.Register;
import com.enums.Period;
import com.enums.PresenceStatus;
import com.enums.StatusCourse;
import com.enums.StatusRegister;
import com.reports.ReportProperties;
import com.repositories.CourseClassRepository;
import com.repositories.CourseRepository;
import com.repositories.PersonRepository;
import com.repositories.PresenceRepository;
import com.repositories.RegisterRepository;
import com.services.PdfService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfRegistrationDateTimeTests {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 25);

    @Mock private CourseRepository courses;
    @Mock private PersonRepository people;
    @Mock private RegisterRepository registers;
    @Mock private CourseClassRepository classes;
    @Mock private PresenceRepository presences;

    private PdfService service;
    private Course course;
    private Person person;
    private Register registration;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(DAY.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new PdfService(courses, people, registers, classes, presences, new ReportProperties(), clock);
        course = new Course("Example course", null, DAY.minusDays(1), DAY.plusDays(1), StatusCourse.ACTIVE, 20);
        course.setId(3L);
        person = new Person();
        person.setId(7L);
        person.setFullName("Example participant");
        registration = new Register(person, course, DAY.atTime(15, 30, 45), StatusRegister.ACTIVE);
        registration.setId(17L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"proof", "enrollments"})
    void formatsRegistrationTimestampAsCalendarDate(String report) throws IOException {
        byte[] bytes;
        if (report.equals("proof")) {
            when(registers.findById(17L)).thenReturn(Optional.of(registration));
            when(classes.findByCourse(course)).thenReturn(List.of());
            bytes = service.registrationProof(17L);
        } else {
            when(courses.findById(3L)).thenReturn(Optional.of(course));
            when(registers.findByCourseOfInterest_IdOrderByPerson_FullNameAsc(3L))
                    .thenReturn(List.of(registration));
            bytes = service.enrollmentList(3L, false);
        }

        String text = text(bytes);
        assertTrue(text.contains("25/11/2026"), text);
        assertFalse(text.contains("15:30"), text);
        assertEquals(DAY.atTime(15, 30, 45), registration.getRegisterDate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"attendance", "history", "declaration"})
    void comparesClassDatesWithRegistrationDayAndPreservesSameDayEligibility(String report) throws IOException {
        CourseClass beforeRegistration = lesson(11L, DAY.minusDays(1));
        CourseClass onRegistrationDay = lesson(12L, DAY);
        when(classes.findByCourse(course)).thenReturn(List.of(beforeRegistration, onRegistrationDay));
        when(presences.findByCourseClass_Course_Id(3L)).thenReturn(List.of(
                new Presence(person, beforeRegistration, course, PresenceStatus.PRESENT),
                new Presence(person, onRegistrationDay, course, PresenceStatus.PRESENT)));

        byte[] bytes;
        switch (report) {
            case "attendance" -> {
                when(courses.findById(3L)).thenReturn(Optional.of(course));
                when(registers.findByCourseOfInterest_IdOrderByPerson_FullNameAsc(3L))
                        .thenReturn(List.of(registration));
                bytes = service.attendanceReport(3L, null, null);
            }
            case "history" -> {
                when(people.findById(7L)).thenReturn(Optional.of(person));
                when(registers.findByPerson_IdOrderByRegisterDateDesc(7L)).thenReturn(List.of(registration));
                bytes = service.participationHistory(7L, null, null);
            }
            case "declaration" -> {
                when(registers.findById(17L)).thenReturn(Optional.of(registration));
                bytes = service.participationDeclaration(17L, null, null);
            }
            default -> throw new AssertionError("Unexpected report: " + report);
        }

        String text = text(bytes);
        switch (report) {
            case "attendance" -> assertTrue(text.contains("Ativa 1 0 0 0 100,00%"), text);
            case "history" -> {
                assertTrue(text.contains("1/1"), text);
                assertTrue(text.contains("1 h 0 min"), text);
            }
            case "declaration" -> {
                assertTrue(text.contains("25/11/2026 a 25/11/2026"), text);
                assertTrue(text.contains("1 h 0 min"), text);
            }
            default -> throw new AssertionError("Unexpected report: " + report);
        }
    }

    private CourseClass lesson(long id, LocalDate day) {
        CourseClass lesson = new CourseClass(day, Period.MORNING, LocalTime.of(9, 0), LocalTime.of(10, 0), course);
        lesson.setId(id);
        return lesson;
    }

    private String text(byte[] bytes) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }
}
