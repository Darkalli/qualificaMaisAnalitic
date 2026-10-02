package com.example.qualificamaisanalitic;

import com.dtos.courseClassesDtos.AddCourseClassDto;
import com.dtos.courseClassesDtos.UpdateCourseClassDto;
import com.dtos.presenceDtos.AddPresenceDto;
import com.dtos.registerDtos.AddRegisterDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.enums.PresenceStatus;
import com.enums.StatusClass;
import com.repositories.*;
import com.services.CourseClassService;
import com.services.PresenceService;
import com.services.RegisterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Cada chamada de serviço tem sua própria transação; os asserts leem o estado confirmado. */
@SpringBootTest
@ActiveProfiles("test")
class ClassScheduleLifecycleTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    private static final String CPF = "01234567890";
    @Autowired private CourseClassService service;
    @Autowired private RegisterService registration;
    @Autowired private PresenceService attendance;
    @Autowired private CourseRepository courses;
    @Autowired private CourseClassRepository classes;
    @Autowired private PersonRepository people;
    @Autowired private PresenceRepository presences;
    @Autowired private JdbcTemplate jdbc;
    private Course first;
    private Course second;
    private Long personId;

    @BeforeEach
    void prepare() {
        clearDatabase();
        first = courses.saveAndFlush(new Course("Curso A", null, DAY, DAY.plusDays(10)));
        second = courses.saveAndFlush(new Course("Curso B", null, DAY, DAY.plusDays(10)));
        personId = people.saveAndFlush(PersonTestData.person(CPF)).getId();
    }

    @AfterEach
    void clearDatabase() {
        for (String table : List.of("presence", "register", "course_class", "person_disabilities", "person", "address", "course")) {
            jdbc.update("delete from " + table);
        }
    }

    @Test
    void creatingAClassRechecksPeopleAlreadyEnrolledInTheCourse() {
        registerBoth();
        add(first, DAY, 8, 10);

        assertThrows(IllegalArgumentException.class, () -> add(second, DAY, 9, 11));

        assertEquals(1, classes.count());
        assertEquals(2, jdbc.queryForObject("select count(*) from register", Integer.class));
        assertDoesNotThrow(() -> add(second, DAY, 10, 12));
    }

    @Test
    void reschedulingRejectsConflictAndRollsBackEveryFieldOfThePatch() {
        registerBoth();
        add(first, DAY, 8, 10);
        var changed = add(second, DAY.plusDays(1), 9, 11);

        assertThrows(IllegalArgumentException.class, () -> service.updateCourseClass(
                new UpdateCourseClassDto(changed.getId(), DAY, "Não salvar", null, null, null)));

        var saved = classes.findById(changed.getId()).orElseThrow();
        assertEquals(DAY.plusDays(1), saved.getDay());
        assertEquals("Sessão", saved.getSession());
        service.updateCourseClass(new UpdateCourseClassDto(changed.getId(), DAY, null, LocalTime.of(10, 0), LocalTime.of(12, 0), null));
        assertEquals(DAY, classes.findById(changed.getId()).orElseThrow().getDay());
    }

    @ParameterizedTest
    @ValueSource(strings = {"start", "finish", "equal"})
    void partialUpdatesValidateFinalTimesBeforeChangingAnything(String field) {
        var original = add(first, DAY, 8, 10);
        LocalTime start = field.equals("start") ? LocalTime.of(11, 0) : null;
        LocalTime finish = field.equals("finish") ? LocalTime.of(7, 0) : field.equals("equal") ? LocalTime.of(8, 0) : null;

        assertThrows(IllegalArgumentException.class, () -> service.updateCourseClass(
                new UpdateCourseClassDto(original.getId(), null, "Não salvar", start, finish, null)));

        var saved = classes.findById(original.getId()).orElseThrow();
        assertEquals(LocalTime.of(8, 0), saved.getStart());
        assertEquals(LocalTime.of(10, 0), saved.getFinish());
        assertEquals("Sessão", saved.getSession());
    }

    @ParameterizedTest
    @ValueSource(strings = {"equal", "inverted", "missing"})
    void creationRejectsInvalidTimes(String value) {
        LocalTime start = value.equals("missing") ? null : LocalTime.of(10, 0);
        LocalTime finish = LocalTime.of(value.equals("inverted") ? 8 : 10, 0);
        assertThrows(IllegalArgumentException.class, () -> service.addCourseClass(
                new AddCourseClassDto(DAY, "Sessão", start, finish, first.getId())));
        assertEquals(0, classes.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELED", "POSTPONED"})
    void inactiveClassesPreserveAttendanceAndDoNotBlockOtherCourses(String state) {
        var original = add(first, DAY, 8, 10);
        registration.addRegister(new AddRegisterDto(CPF, first.getId(), DAY));
        var presence = attendance.addPresence(new AddPresenceDto(personId, original.getId(), PresenceStatus.PRESENT));
        if (state.equals("CANCELED")) {
            service.deleteCourseClass(original.getId());
            service.deleteCourseClass(original.getId());
        } else {
            changeStatus(original, StatusClass.POSTPONED);
        }
        var saved = classes.findById(original.getId()).orElseThrow();
        assertEquals(StatusClass.valueOf(state), saved.getStatusClass());
        assertEquals(PresenceStatus.PRESENT, presences.findById(presence.getId()).orElseThrow().getStatus());
        Long anotherPerson = people.saveAndFlush(PersonTestData.person("98765432100")).getId();
        registration.addRegister(new AddRegisterDto("98765432100", first.getId(), DAY));
        assertThrows(IllegalArgumentException.class, () -> attendance.addPresence(
                new AddPresenceDto(anotherPerson, original.getId(), PresenceStatus.PRESENT)));
        add(second, DAY, 9, 11);
        registration.addRegister(new AddRegisterDto(CPF, second.getId(), DAY));
        assertThrows(IllegalArgumentException.class, () -> changeStatus(original, StatusClass.ACTIVE));
        assertEquals(StatusClass.valueOf(state), classes.findById(original.getId()).orElseThrow().getStatusClass());
        assertEquals(1, presences.count());
        // A regra de uma aula por curso/dia também preserva a ocorrência cancelada.
        assertThrows(IllegalArgumentException.class, () -> add(first, DAY, 14, 16));
    }

    @ParameterizedTest
    @ValueSource(strings = {"day", "course", "start", "finish"})
    void attendanceProtectsOriginalClassIdentityAndSchedule(String field) {
        var original = add(first, DAY, 8, 10);
        registration.addRegister(new AddRegisterDto(CPF, first.getId(), DAY));
        attendance.addPresence(new AddPresenceDto(personId, original.getId(), PresenceStatus.PRESENT));

        assertThrows(IllegalArgumentException.class, () -> service.updateCourseClass(new UpdateCourseClassDto(
                original.getId(), field.equals("day") ? DAY.plusDays(1) : null, null,
                field.equals("start") ? LocalTime.of(9, 0) : null,
                field.equals("finish") ? LocalTime.of(11, 0) : null,
                field.equals("course") ? second : null)));

        var saved = classes.findById(original.getId()).orElseThrow();
        assertEquals(first.getId(), saved.getCourse().getId());
        assertEquals(DAY, saved.getDay());
        assertEquals(LocalTime.of(8, 0), saved.getStart());
        assertEquals(LocalTime.of(10, 0), saved.getFinish());
    }

    @Test
    void postponedClassWithoutAttendanceCanBeRescheduledAndReactivated() {
        registerBoth();
        add(first, DAY, 8, 10);
        var original = add(second, DAY, 10, 12);
        changeStatus(original, StatusClass.POSTPONED);
        service.updateCourseClass(new UpdateCourseClassDto(original.getId(), DAY.plusDays(1), null,
                LocalTime.of(8, 0), LocalTime.of(10, 0), null, StatusClass.ACTIVE));
        var saved = classes.findById(original.getId()).orElseThrow();
        assertEquals(StatusClass.ACTIVE, saved.getStatusClass());
        assertEquals(DAY.plusDays(1), saved.getDay());
    }

    private void registerBoth() {
        registration.addRegister(new AddRegisterDto(CPF, first.getId(), DAY));
        registration.addRegister(new AddRegisterDto(CPF, second.getId(), DAY));
    }

    private CourseClass add(Course course, LocalDate day, int start, int finish) {
        return service.addCourseClass(new AddCourseClassDto(day, "Sessão", LocalTime.of(start, 0), LocalTime.of(finish, 0), course.getId()));
    }

    private void changeStatus(CourseClass courseClass, StatusClass status) {
        service.updateCourseClass(new UpdateCourseClassDto(courseClass.getId(), null, null, null, null, null, status));
    }
}
