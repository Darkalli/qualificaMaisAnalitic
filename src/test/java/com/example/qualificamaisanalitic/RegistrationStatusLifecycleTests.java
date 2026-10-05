package com.example.qualificamaisanalitic;

import com.dtos.courseClassesDtos.AddCourseClassDto;
import com.dtos.courseClassesDtos.UpdateCourseClassDto;
import com.dtos.presenceDtos.AddPresenceDto;
import com.dtos.registerDtos.AddRegisterDto;
import com.dtos.registerDtos.SearchRegisterDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Register;
import com.enums.PresenceStatus;
import com.enums.StatusRegister;
import com.exceptions.ConflictException;
import com.repositories.*;
import com.services.CourseClassService;
import com.services.PresenceService;
import com.services.RegisterService;
import com.sheets.SheetImportResult;
import com.sheets.services.RegisterPersistenceService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real services and committed SQL reads, with independent connections for competing transactions. */
@SpringBootTest
@ActiveProfiles("test")
class RegistrationStatusLifecycleTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    private static final String CPF = "01234567890";
    private static final String OTHER_CPF = "98765432100";
    @Autowired private RegisterService registration;
    @Autowired private PresenceService attendance;
    @Autowired private CourseClassService schedule;
    @Autowired private RegisterPersistenceService imports;
    @Autowired private PersonRepository people;
    @Autowired private CourseRepository courses;
    @Autowired private CourseClassRepository classes;
    @Autowired private RegisterRepository registers;
    @Autowired private PresenceRepository presences;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    @Value("${spring.datasource.driver-class-name}") private String driver;
    private Course first;
    private Course second;
    private Long personId;

    @BeforeEach
    void prepare() {
        clearDatabase();
        first = courses.saveAndFlush(new Course("A", null, DAY, DAY.plusDays(10)));
        second = courses.saveAndFlush(new Course("B", null, DAY, DAY.plusDays(10)));
        personId = people.saveAndFlush(PersonTestData.person(CPF)).getId();
    }

    @AfterEach
    void clearDatabase() {
        for (String table : List.of("presence", "register", "course_class", "person_disabilities", "person", "address", "course")) {
            jdbc.update("delete from " + table);
        }
    }

    @Test
    void cancellationPreservesRegistrationAndAttendanceAndIsIdempotent() {
        var lesson = addClass(first, DAY, 8, 10);
        var enrolled = enroll(first);
        var presence = attendance.addPresence(new AddPresenceDto(personId, lesson.getId(), PresenceStatus.PRESENT));
        cancel(first);
        cancel(first);
        var saved = registers.findById(enrolled.getId()).orElseThrow();
        assertEquals(StatusRegister.CANCELED, saved.getStatus());
        assertEquals(DAY, saved.getRegisterDate());
        assertEquals(1, registers.count());
        assertEquals(PresenceStatus.PRESENT, presences.findById(presence.getId()).orElseThrow().getStatus());
        var nextLesson = addClass(first, DAY.plusDays(1), 8, 10);
        assertThrows(ConflictException.class, () -> attendance.addPresence(
                new AddPresenceDto(personId, nextLesson.getId(), PresenceStatus.PRESENT)));
        assertEquals(1, presences.count());
    }

    @Test
    void repeatedImportDoesNotReactivateCanceledRegistrationOrOverwriteDate() {
        var enrolled = enroll(first);
        cancel(first);
        importRow(CPF, first);
        importRow(CPF, first);
        assertEquals(1, registers.count());
        var saved = registers.findById(enrolled.getId()).orElseThrow();
        assertEquals(StatusRegister.CANCELED, saved.getStatus());
        assertEquals(DAY, saved.getRegisterDate());
        assertEquals(1, people.count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void changedCpfOrCourseInSheetCreatesNewEntryAndPreservesCanceledHistory(boolean changedCpf) {
        var lesson = addClass(first, DAY, 8, 10);
        var old = enroll(first);
        var presence = attendance.addPresence(new AddPresenceDto(personId, lesson.getId(), PresenceStatus.PRESENT));
        cancel(first);
        importRow(changedCpf ? OTHER_CPF : CPF, changedCpf ? first : second);
        importRow(changedCpf ? OTHER_CPF : CPF, changedCpf ? first : second);
        assertEquals(2, registers.count());
        assertEquals(StatusRegister.CANCELED, registers.findById(old.getId()).orElseThrow().getStatus());
        assertEquals(1, jdbc.queryForObject("select count(*) from register where status='ACTIVE'", Integer.class));
        assertEquals(changedCpf ? 2 : 1, people.count());
        assertEquals(PresenceStatus.PRESENT, presences.findById(presence.getId()).orElseThrow().getStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void classCreationOrRescheduleIgnoresCanceledEnrollee(boolean create) {
        enroll(first);
        cancel(first);
        addClass(second, DAY, 9, 11);
        enroll(second);
        if (create) {
            assertDoesNotThrow(() -> addClass(first, DAY, 8, 10));
        } else {
            var old = addClass(first, DAY.plusDays(1), 8, 10);
            assertDoesNotThrow(() -> moveToDay(old, DAY));
            assertEquals(DAY, classes.findById(old.getId()).orElseThrow().getDay());
        }
        assertEquals(StatusRegister.CANCELED, state(first));
        assertEquals(2, classes.count());
    }

    @Test
    void conflictRejectsReactivationAndRollsBackWithoutChangingHistory() {
        var lesson = addClass(first, DAY, 8, 10);
        var original = enroll(first);
        var presence = attendance.addPresence(new AddPresenceDto(personId, lesson.getId(), PresenceStatus.PRESENT));
        cancel(first);
        addClass(second, DAY, 9, 11);
        enroll(second);
        assertThrows(ConflictException.class, () -> reactivate(first));
        assertEquals(StatusRegister.CANCELED, state(first));
        assertEquals(DAY, registers.findById(original.getId()).orElseThrow().getRegisterDate());
        assertEquals(2, registers.count());
        assertEquals(PresenceStatus.PRESENT, presences.findById(presence.getId()).orElseThrow().getStatus());
    }

    @Test
    void nonConflictingReactivationKeepsSameIdentityAndIsIdempotent() {
        var original = enroll(first);
        cancel(first);
        reactivate(first);
        reactivate(first);
        assertEquals(StatusRegister.ACTIVE, state(first));
        assertEquals(1, registers.count());
        assertEquals(original.getId(), registration.getByPersonCpfAndCourseOfInterest(key(first)).getId());
        assertEquals(DAY, registers.findById(original.getId()).orElseThrow().getRegisterDate());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancellationAndAttendanceWaitThenObserveCommittedStatus(boolean cancellationFirst) throws Exception {
        var lesson = addClass(first, DAY, 8, 10);
        enroll(first);
        Runnable presence = () -> attendance.addPresence(new AddPresenceDto(personId, lesson.getId(), PresenceStatus.PRESENT));
        var failure = race(cancellationFirst ? () -> cancel(first) : presence,
                cancellationFirst ? presence : () -> cancel(first));
        if (cancellationFirst) {
            assertInstanceOf(ConflictException.class, failure);
            assertEquals(0, presences.count());
        } else {
            assertNull(failure);
            assertEquals(1, presences.count());
            assertEquals("PRESENT", jdbc.queryForObject("select status from presence", String.class));
        }
        assertEquals(StatusRegister.CANCELED, state(first));
        assertEquals(1, registers.count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reactivationAndNewRegistrationWaitThenRejectCommittedScheduleConflict(boolean reactivationFirst) throws Exception {
        addClass(first, DAY, 8, 10);
        enroll(first);
        cancel(first);
        addClass(second, DAY, 9, 11);
        var failure = race(reactivationFirst ? () -> reactivate(first) : () -> enroll(second),
                reactivationFirst ? () -> enroll(second) : () -> reactivate(first));
        assertInstanceOf(ConflictException.class, failure, "Business rejection is required; lock failures are not success");
        assertEquals(reactivationFirst ? StatusRegister.ACTIVE : StatusRegister.CANCELED, state(first));
        assertEquals(reactivationFirst ? 1 : 2, registers.count());
        assertEquals(reactivationFirst ? 0 : 1, jdbc.queryForObject("select count(*) from register where course_id=?", Integer.class, second.getId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reactivationAndClassChangeWaitThenRejectConflictWithoutPartialPatch(boolean reactivationFirst) throws Exception {
        addClass(first, DAY, 8, 10);
        enroll(first);
        cancel(first);
        var lesson = addClass(second, DAY.plusDays(1), 9, 11);
        enroll(second);
        var failure = race(reactivationFirst ? () -> reactivate(first) : () -> moveToDay(lesson, DAY),
                reactivationFirst ? () -> moveToDay(lesson, DAY) : () -> reactivate(first));
        assertInstanceOf(ConflictException.class, failure, "Business rejection is required; deadlocks are failures");
        assertEquals(reactivationFirst ? StatusRegister.ACTIVE : StatusRegister.CANCELED, state(first));
        var saved = classes.findById(lesson.getId()).orElseThrow();
        assertEquals(reactivationFirst ? DAY.plusDays(1) : DAY, saved.getDay());
        assertEquals(reactivationFirst ? "Session" : "Moved", saved.getSession());
        assertEquals(LocalTime.of(9, 0), saved.getStart());
        assertEquals(LocalTime.of(11, 0), saved.getFinish());
        assertEquals(2, registers.count());
    }

    /** The first actual service write is held uncommitted while a second connection starts its service call. */
    private Throwable race(Runnable firstOperation, Runnable secondOperation) throws Exception {
        var firstWritten = new CountDownLatch(1);
        var releaseCommit = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var firstConnection = new AtomicInteger();
        var secondConnection = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstTask = executor.submit(() -> {
                try {
                    transactions.executeWithoutResult(tx -> {
                        firstConnection.set(jdbc.queryForObject(connectionIdSql(), Integer.class));
                        firstOperation.run();
                        // A JPA status mutation must reach SQL before the competitor starts.
                        registers.flush();
                        firstWritten.countDown();
                        await(releaseCommit);
                    });
                } finally {
                    firstWritten.countDown();
                }
            });
            await(firstWritten);
            if (firstTask.isDone()) firstTask.get(10, TimeUnit.SECONDS);
            var secondTask = executor.submit(() -> {
                try {
                    transactions.executeWithoutResult(tx -> {
                        secondConnection.set(jdbc.queryForObject(connectionIdSql(), Integer.class));
                        secondStarted.countDown();
                        secondOperation.run();
                    });
                    return (Throwable) null;
                } catch (Throwable error) {
                    return error;
                } finally {
                    secondStarted.countDown();
                }
            });
            await(secondStarted);
            assertNotEquals(firstConnection.get(), secondConnection.get(), "Competing transactions need independent connections");
            try {
                assertThrows(TimeoutException.class, () -> secondTask.get(500, TimeUnit.MILLISECONDS),
                        "The second service operation must wait for the first transaction");
            } finally {
                releaseCommit.countDown();
            }
            firstTask.get(10, TimeUnit.SECONDS);
            return secondTask.get(10, TimeUnit.SECONDS);
        } finally {
            releaseCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Transaction did not reach its synchronization point");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private String connectionIdSql() {
        return "org.postgresql.Driver".equals(driver) ? "select pg_backend_pid()" : "select session_id()";
    }

    private Register enroll(Course course) {
        return registration.addRegister(new AddRegisterDto(CPF, course.getId(), DAY));
    }

    private void cancel(Course course) {
        registration.deleteRegister(key(course));
    }

    private void reactivate(Course course) {
        registration.reactiveRegister(key(course));
    }

    private SearchRegisterDto key(Course course) {
        return new SearchRegisterDto(CPF, course.getId());
    }

    private StatusRegister state(Course course) {
        return StatusRegister.valueOf(jdbc.queryForObject("select status from register where person_id=? and course_id=?",
                String.class, personId, course.getId()));
    }

    private CourseClass addClass(Course course, LocalDate day, int start, int finish) {
        return schedule.addCourseClass(new AddCourseClassDto(day, "Session", LocalTime.of(start, 0), LocalTime.of(finish, 0), course.getId()));
    }

    private void moveToDay(CourseClass lesson, LocalDate day) {
        schedule.updateCourseClass(new UpdateCourseClassDto(lesson.getId(), day, "Moved", null, null, null));
    }

    private void importRow(String cpf, Course course) {
        imports.persist(new SheetImportResult(List.of(RegisterTestData.register(cpf, course.getId())), List.of(), 0));
    }
}
