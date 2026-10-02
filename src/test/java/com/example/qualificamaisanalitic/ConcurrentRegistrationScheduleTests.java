package com.example.qualificamaisanalitic;

import com.dtos.registerDtos.AddRegisterDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.repositories.CourseClassRepository;
import com.repositories.CourseRepository;
import com.repositories.PersonRepository;
import com.services.RegisterService;
import com.sheets.SheetImportResult;
import com.sheets.services.RegisterPersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/** Serviços reais em conexões separadas: a primeira gravação fica aberta enquanto a segunda tenta inscrever. */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentRegistrationScheduleTests {
    private static final String CPF = "01234567890";
    private static final String OTHER_CPF = "98765432100";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    @Autowired private RegisterService direct;
    @Autowired private RegisterPersistenceService imported;
    @Autowired private PersonRepository people;
    @Autowired private CourseRepository courses;
    @Autowired private CourseClassRepository classes;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    private Course firstCourse;
    private Course secondCourse;

    @BeforeEach
    void prepareCatalog() {
        clearDatabase();
        firstCourse = courses.saveAndFlush(new Course("Curso A", null, DAY, DAY.plusDays(1)));
        secondCourse = courses.saveAndFlush(new Course("Curso B", null, DAY, DAY.plusDays(1)));
        classes.saveAndFlush(new CourseClass(DAY, "Manhã", LocalTime.of(8, 0), LocalTime.of(10, 0), firstCourse));
    }

    @AfterEach
    void clearDatabase() {
        for (String table : List.of("presence", "register", "course_class", "person_disabilities", "person", "address", "course")) {
            jdbc.update("delete from " + table);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "api, api, true", "api, sheets, true", "sheets, api, true", "sheets, sheets, true",
            "api, api, false", "api, sheets, false", "sheets, api, false", "sheets, sheets, false"
    })
    void serializesRegistrationForExistingPersonAndChecksCommittedSchedules(String first, String second, boolean conflict)
            throws Exception {
        people.saveAndFlush(PersonTestData.person(CPF));
        addSecondClass(conflict);

        Throwable rejected = runWhileFirstRegistrationIsUncommitted(first, second, CPF, true);

        if (conflict) {
            assertInstanceOf(IllegalArgumentException.class, rejected);
            assertTrue(rejected.getMessage().contains("hor"));
            assertEquals(1, count("register"));
            assertEquals(0, jdbc.queryForObject("select count(*) from register where course_id = ?", Integer.class, secondCourse.getId()));
        } else {
            assertNull(rejected);
            assertEquals(2, count("register"));
        }
        assertEquals(1, count("person"));
        assertEquals(1, count("address"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void simultaneousImportsOfNewCpfRollBackDuplicatePersonAndCanBeRetried(boolean conflict) throws Exception {
        addSecondClass(conflict);

        Throwable rejected = runWhileFirstRegistrationIsUncommitted("sheets", "sheets", CPF, true);

        // Pessoa ainda ausente não possui linha para bloquear: a unicidade de CPF protege a criação.
        assertInstanceOf(DataIntegrityViolationException.class, rejected);
        assertEquals(1, count("person"));
        assertEquals(1, count("address"));
        assertEquals(2, count("person_disabilities"));
        assertEquals(1, count("register"));
        if (conflict) {
            assertThrows(IllegalArgumentException.class, () -> register("sheets", CPF, secondCourse));
            assertEquals(1, count("register"));
        } else {
            register("sheets", CPF, secondCourse);
            assertEquals(2, count("register"));
        }
        assertEquals(1, count("person"));
        assertEquals(1, count("address"));
    }

    @Test
    void differentPeopleCanRegisterWithoutWaitingForEachOthersTransaction() throws Exception {
        people.saveAndFlush(PersonTestData.person(CPF));
        people.saveAndFlush(PersonTestData.person(OTHER_CPF));
        addSecondClass(true);

        assertNull(runWhileFirstRegistrationIsUncommitted("api", "api", OTHER_CPF, false));

        assertEquals(2, count("register"));
    }

    private Throwable runWhileFirstRegistrationIsUncommitted(String first, String second, String secondCpf, boolean mustWait)
            throws Exception {
        var firstWritten = new CountDownLatch(1);
        var allowCommit = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstTask = executor.submit(() -> transactions.executeWithoutResult(status -> {
                register(first, CPF, firstCourse);
                firstWritten.countDown();
                await(allowCommit);
            }));
            await(firstWritten);
            var secondTask = executor.submit(() -> {
                secondStarted.countDown();
                try {
                    register(second, secondCpf, secondCourse);
                    return (Throwable) null;
                } catch (IllegalArgumentException | DataIntegrityViolationException error) {
                    return error;
                }
            });
            await(secondStarted);
            try {
                if (mustWait) {
                    assertThrows(TimeoutException.class, () -> secondTask.get(500, TimeUnit.MILLISECONDS),
                            "A segunda inscrição deve aguardar a transação da mesma pessoa");
                } else {
                    assertNull(secondTask.get(5, TimeUnit.SECONDS));
                }
            } finally {
                allowCommit.countDown();
            }
            firstTask.get(10, TimeUnit.SECONDS);
            return secondTask.get(10, TimeUnit.SECONDS);
        } finally {
            allowCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private void register(String flow, String cpf, Course course) {
        if (flow.equals("api")) {
            direct.addRegister(new AddRegisterDto(cpf, course.getId(), DAY));
        } else {
            imported.persist(new SheetImportResult(List.of(RegisterTestData.register(cpf, course.getId())), List.of(), 0));
        }
    }

    private void addSecondClass(boolean conflict) {
        classes.saveAndFlush(new CourseClass(DAY, "Manhã", LocalTime.of(conflict ? 9 : 10, 0), LocalTime.of(12, 0), secondCourse));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "A transação não alcançou o ponto de sincronização");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
