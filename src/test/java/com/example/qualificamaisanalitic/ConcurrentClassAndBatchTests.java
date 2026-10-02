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
import com.sheets.SheetImportResult;
import com.sheets.services.RegisterPersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentClassAndBatchTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    private static final String CPF = "01234567890";
    private static final String OTHER_CPF = "98765432100";
    @Autowired private CourseRepository courses;
    @Autowired private PersonRepository people;
    @Autowired private CourseClassRepository classes;
    @Autowired private CourseClassService classService;
    @Autowired private RegisterService registrations;
    @Autowired private RegisterPersistenceService imports;
    @Autowired private PresenceService attendance;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    private Course first;
    private Course second;

    @BeforeEach
    void prepare() {
        clearDatabase();
        first = courses.saveAndFlush(new Course("Curso A", null, DAY, DAY.plusDays(10)));
        second = courses.saveAndFlush(new Course("Curso B", null, DAY, DAY.plusDays(10)));
    }

    @AfterEach
    void clearDatabase() {
        for (String table : List.of("presence", "register", "course_class", "person_disabilities", "person", "address", "course")) {
            jdbc.update("delete from " + table);
        }
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void reversedPeopleInSimultaneousBatchesCompleteOrRollBackWithoutDeadlock(boolean newPeople, boolean conflict) throws Exception {
        if (!newPeople) {
            people.saveAndFlush(PersonTestData.person(CPF));
            people.saveAndFlush(PersonTestData.person(OTHER_CPF));
        }
        add(first, DAY, 8, 10);
        add(second, DAY, conflict ? 9 : 10, 12);
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> {
                ready.countDown(); await(start);
                return attempt(() -> batch(first, CPF, OTHER_CPF));
            });
            var b = executor.submit(() -> {
                ready.countDown(); await(start);
                return attempt(() -> batch(second, OTHER_CPF, CPF));
            });
            await(ready);
            start.countDown();
            Throwable errorA = a.get(15, TimeUnit.SECONDS);
            Throwable errorB = b.get(15, TimeUnit.SECONDS);
            assertTrue(errorA == null || errorB == null, "Ao menos um lote deve confirmar por inteiro");
            assertEquals(2, count("person"));
            assertEquals(2, count("address"));
            if (conflict) {
                assertTrue(errorA != null || errorB != null);
                assertEquals(2, count("register"));
                Course rejectedCourse = errorA == null ? second : first;
                assertEquals(0, jdbc.queryForObject("select count(*) from register where course_id = ?", Integer.class, rejectedCourse.getId()));
                assertThrows(IllegalArgumentException.class, () -> batch(rejectedCourse, CPF, OTHER_CPF));
            } else {
                // CPF novo pode disputar a inserção única; uma nova chamada usa objetos novos.
                if (errorA != null) batch(first, CPF, OTHER_CPF);
                if (errorB != null) batch(second, OTHER_CPF, CPF);
                assertEquals(4, count("register"));
            }
            assertEquals(2, count("person"));
            assertEquals(2, count("address"));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @CsvSource({"create, true", "create, false", "reschedule, true", "reschedule, false", "reactivate, true", "reactivate, false"})
    void classChangesAndRegistrationCannotCommitConflictingSchedules(String change, boolean registrationFirst) throws Exception {
        people.saveAndFlush(PersonTestData.person(CPF));
        add(first, DAY, 8, 10);
        registrations.addRegister(new AddRegisterDto(CPF, first.getId(), DAY));
        CourseClass candidate;
        if (change.equals("create")) {
            candidate = null;
        } else {
            candidate = add(second, change.equals("reschedule") ? DAY.plusDays(1) : DAY, 9, 11);
            if (change.equals("reactivate")) classService.deleteCourseClass(candidate.getId());
        }
        Runnable changeClass = () -> {
            if (change.equals("create")) add(second, DAY, 9, 11);
            else classService.updateCourseClass(new UpdateCourseClassDto(candidate.getId(), DAY, null, null, null, null, StatusClass.ACTIVE));
        };
        Runnable register = () -> registrations.addRegister(new AddRegisterDto(CPF, second.getId(), DAY));

        Throwable rejected = overlap(registrationFirst ? register : changeClass, registrationFirst ? changeClass : register);

        assertInstanceOf(IllegalArgumentException.class, rejected);
        assertEquals(registrationFirst ? 2 : 1, count("register"));
        if (registrationFirst && candidate != null) {
            var saved = classes.findById(candidate.getId()).orElseThrow();
            if (change.equals("reschedule")) assertEquals(DAY.plusDays(1), saved.getDay());
            else assertEquals(StatusClass.CANCELED, saved.getStatusClass());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancelingAndRecordingAttendancePreserveAConsistentHistory(boolean attendanceFirst) throws Exception {
        Long personId = people.saveAndFlush(PersonTestData.person(CPF)).getId();
        var courseClass = add(first, DAY, 8, 10);
        registrations.addRegister(new AddRegisterDto(CPF, first.getId(), DAY));
        Runnable record = () -> attendance.addPresence(new AddPresenceDto(personId, courseClass.getId(), PresenceStatus.PRESENT));
        Runnable cancel = () -> classService.deleteCourseClass(courseClass.getId());

        Throwable result = overlap(attendanceFirst ? record : cancel, attendanceFirst ? cancel : record);

        if (attendanceFirst) assertNull(result);
        else assertInstanceOf(IllegalArgumentException.class, result);
        assertEquals(attendanceFirst ? 1 : 0, count("presence"));
        assertEquals(StatusClass.CANCELED, classes.findById(courseClass.getId()).orElseThrow().getStatusClass());
    }

    private Throwable overlap(Runnable firstOperation, Runnable secondOperation) throws Exception {
        var firstWritten = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> transactions.executeWithoutResult(status -> {
                firstOperation.run(); firstWritten.countDown(); await(commit);
            }));
            await(firstWritten);
            var b = executor.submit(() -> {
                secondStarted.countDown();
                return attempt(secondOperation);
            });
            await(secondStarted);
            try {
                assertThrows(TimeoutException.class, () -> b.get(400, TimeUnit.MILLISECONDS));
            } finally {
                commit.countDown();
            }
            a.get(10, TimeUnit.SECONDS);
            return b.get(10, TimeUnit.SECONDS);
        } finally {
            commit.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private Throwable attempt(Runnable operation) {
        try { operation.run(); return null; }
        catch (IllegalArgumentException | DataIntegrityViolationException error) { return error; }
    }

    private void batch(Course course, String firstCpf, String secondCpf) {
        imports.persist(new SheetImportResult(List.of(RegisterTestData.register(firstCpf, course.getId()),
                RegisterTestData.register(secondCpf, course.getId())), List.of(), 0));
    }

    private CourseClass add(Course course, LocalDate day, int start, int finish) {
        return classService.addCourseClass(new AddCourseClassDto(day, "Sessão", LocalTime.of(start, 0), LocalTime.of(finish, 0), course));
    }

    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }

    private void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS), "Tempo esgotado na sincronização"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
}
