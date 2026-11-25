package com.example.qualificamaisanalitic;

import com.dtos.registerDtos.AddRegisterDto;
import com.dtos.registerDtos.SearchRegisterDto;
import com.entities.Address;
import com.entities.Course;
import com.entities.Person;
import com.entities.Register;
import com.enums.Education;
import com.enums.Gender;
import com.enums.StatusCourse;
import com.enums.StatusRegister;
import com.enums.WorkState;
import com.repositories.CourseRepository;
import com.repositories.PersonRepository;
import com.repositories.RegisterRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/** Uses real transactions and the production migrations in a dedicated in-memory database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:course-waitlist-tests;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@ActiveProfiles("test")
class RegisterWaitlistPersistenceTests {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 25);
    private static final LocalDateTime QUEUED_AT = DAY.atTime(8, 0);
    private static final String FIRST_CPF = "01234567890";
    private static final String SECOND_CPF = "98765432100";

    @Autowired private RegisterService service;
    @Autowired private RegisterRepository registers;
    @Autowired private CourseRepository courses;
    @Autowired private PersonRepository people;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;

    private Course course;

    @BeforeEach
    void prepareCourse() {
        clearDatabase();
        course = saveCourse("Course with one place");
    }

    @AfterEach
    void clearDatabase() {
        for (String table : List.of("presence", "register", "course_class", "person_disabilities",
                "person", "address", "course")) {
            jdbc.update("delete from " + table);
        }
    }

    @Test
    void countsOnlyActiveRegistrationsOfRequestedCourse() {
        saveRegistration(FIRST_CPF, course, StatusRegister.ACTIVE, QUEUED_AT);
        saveRegistration(SECOND_CPF, course, StatusRegister.WAITLISTED, QUEUED_AT.plusMinutes(1));
        saveRegistration("12345678901", course, StatusRegister.CANCELED, QUEUED_AT.plusMinutes(2));
        Course otherCourse = saveCourse("Another course");
        saveRegistration("23456789012", otherCourse, StatusRegister.ACTIVE, QUEUED_AT);

        assertEquals(1, registers.countAllActiveByCourseId(course.getId()));
        assertEquals(1, registers.countAllActiveByCourseId(otherCourse.getId()));
        assertEquals(0, registers.countAllActiveByCourseId(Long.MAX_VALUE));
    }

    @Test
    void selectsOldestReserveByTimestampWithinRequestedCourse() {
        Register newer = saveRegistration(FIRST_CPF, course, StatusRegister.WAITLISTED,
                QUEUED_AT.plusSeconds(1));
        Register oldest = saveRegistration(SECOND_CPF, course, StatusRegister.WAITLISTED, QUEUED_AT);
        saveRegistration("12345678901", course, StatusRegister.CANCELED, QUEUED_AT.minusHours(1));
        saveRegistration("23456789012", course, StatusRegister.ACTIVE, QUEUED_AT.minusHours(2));
        saveRegistration("34567890123", saveCourse("Another course"),
                StatusRegister.WAITLISTED, QUEUED_AT.minusHours(3));

        Register selected = registers.findFirstByCourseOfInterestIdAndStatusOrderByRegisterDate(
                course.getId(), StatusRegister.WAITLISTED).orElseThrow();

        assertEquals(oldest.getId(), selected.getId());
        assertNotEquals(newer.getId(), selected.getId());
        assertEquals(QUEUED_AT, selected.getRegisterDate());
        assertEquals(1, courses.findById(course.getId()).orElseThrow().getMaximumStudentsCount());
    }

    @Test
    void returnsEmptyWhenCourseHasNoReserves() {
        saveRegistration(FIRST_CPF, course, StatusRegister.ACTIVE, QUEUED_AT);
        saveRegistration(SECOND_CPF, course, StatusRegister.CANCELED, QUEUED_AT);

        assertTrue(registers.findFirstByCourseOfInterestIdAndStatusOrderByRegisterDate(
                course.getId(), StatusRegister.WAITLISTED).isEmpty());
    }

    @Test
    void cancellationPersistsPromotionOfOnlyOldestReserve() {
        Register active = saveRegistration(FIRST_CPF, course, StatusRegister.ACTIVE, QUEUED_AT);
        Register newer = saveRegistration("12345678901", course, StatusRegister.WAITLISTED,
                QUEUED_AT.plusHours(2));
        Register oldest = saveRegistration(SECOND_CPF, course, StatusRegister.WAITLISTED,
                QUEUED_AT.plusHours(1));

        service.deleteRegister(new SearchRegisterDto(FIRST_CPF, course.getId()));

        assertEquals(StatusRegister.CANCELED, reload(active).getStatus());
        assertEquals(StatusRegister.ACTIVE, reload(oldest).getStatus());
        assertEquals(StatusRegister.WAITLISTED, reload(newer).getStatus());
        assertEquals(QUEUED_AT, reload(active).getRegisterDate());
        assertEquals(QUEUED_AT.plusHours(1), reload(oldest).getRegisterDate());
        assertEquals(1, registers.countAllActiveByCourseId(course.getId()));
        assertEquals(3, registers.count());
    }

    @Test
    void cancellationWithoutReserveCommitsAndReleasesPlace() {
        Register active = saveRegistration(FIRST_CPF, course, StatusRegister.ACTIVE, QUEUED_AT);

        assertDoesNotThrow(() -> service.deleteRegister(new SearchRegisterDto(FIRST_CPF, course.getId())));

        assertEquals(StatusRegister.CANCELED, reload(active).getStatus());
        assertEquals(0, registers.countAllActiveByCourseId(course.getId()));
        assertEquals(1, registers.count());
    }

    @Test
    void repeatedCancellationDoesNotPromoteAdditionalReserve() {
        saveRegistration(FIRST_CPF, course, StatusRegister.ACTIVE, QUEUED_AT);
        Register first = saveRegistration(SECOND_CPF, course, StatusRegister.WAITLISTED,
                QUEUED_AT.plusHours(1));
        Register second = saveRegistration("12345678901", course, StatusRegister.WAITLISTED,
                QUEUED_AT.plusHours(2));
        SearchRegisterDto input = new SearchRegisterDto(FIRST_CPF, course.getId());

        service.deleteRegister(input);
        service.deleteRegister(input);

        assertEquals(StatusRegister.ACTIVE, reload(first).getStatus());
        assertEquals(StatusRegister.WAITLISTED, reload(second).getStatus());
        assertEquals(1, registers.countAllActiveByCourseId(course.getId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentRegistrationWaitsAndRechecksLastPlaceAfterCommitOrRollback(boolean rollBackFirst)
            throws Exception {
        savePerson(FIRST_CPF);
        savePerson(SECOND_CPF);
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch allowFirstToFinish = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> transactions.executeWithoutResult(transaction -> {
                service.addRegister(new AddRegisterDto(FIRST_CPF, course.getId()));
                firstWritten.countDown();
                await(allowFirstToFinish);
                if (rollBackFirst) {
                    transaction.setRollbackOnly();
                }
            }));
            await(firstWritten);
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return service.addRegister(new AddRegisterDto(SECOND_CPF, course.getId()));
            });
            await(secondStarted);
            try {
                assertThrows(TimeoutException.class, () -> second.get(500, TimeUnit.MILLISECONDS),
                        "Another person competing for the same course must wait for its write lock");
            } finally {
                allowFirstToFinish.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
            Register saved = second.get(10, TimeUnit.SECONDS);

            assertEquals(rollBackFirst ? StatusRegister.ACTIVE : StatusRegister.WAITLISTED,
                    saved.getStatus());
            assertEquals(1, registers.countAllActiveByCourseId(course.getId()));
            assertEquals(rollBackFirst ? 1 : 2, registers.count());
            assertEquals(rollBackFirst, registers.findByPerson_CpfAndCourseOfInterest_Id(
                    FIRST_CPF, course.getId()).isEmpty());
            assertEquals(rollBackFirst, registers.findFirstByCourseOfInterestIdAndStatusOrderByRegisterDate(
                    course.getId(), StatusRegister.WAITLISTED).isEmpty());
        } finally {
            allowFirstToFinish.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @Test
    void registrationsInDifferentCoursesDoNotWaitForEachOther() throws Exception {
        savePerson(FIRST_CPF);
        savePerson(SECOND_CPF);
        Course otherCourse = saveCourse("Independent course");
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch allowFirstToFinish = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> transactions.executeWithoutResult(transaction -> {
                service.addRegister(new AddRegisterDto(FIRST_CPF, course.getId()));
                firstWritten.countDown();
                await(allowFirstToFinish);
            }));
            await(firstWritten);
            var second = executor.submit(() ->
                    service.addRegister(new AddRegisterDto(SECOND_CPF, otherCourse.getId())));
            try {
                assertEquals(StatusRegister.ACTIVE, second.get(5, TimeUnit.SECONDS).getStatus());
            } finally {
                allowFirstToFinish.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
            assertEquals(1, registers.countAllActiveByCourseId(course.getId()));
            assertEquals(1, registers.countAllActiveByCourseId(otherCourse.getId()));
        } finally {
            allowFirstToFinish.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    private Course saveCourse(String name) {
        return courses.saveAndFlush(new Course(name, null, DAY, DAY.plusMonths(1),
                StatusCourse.ACTIVE, 1));
    }

    private Person savePerson(String cpf) {
        Person person = new Person();
        person.setFullName("Participant " + cpf);
        person.setCpf(cpf);
        person.setEmail("participant" + cpf + "@example.com");
        person.setPersonalPhone("55999999999");
        person.setPersonalPhoneHasWhatsapp(true);
        person.setAddress(new Address(42, "Example street", "Example neighborhood"));
        person.setGender(Gender.OTHER);
        person.setEducation(Education.HIGH_SCHOOL_COMPLETE);
        person.setWorkState(WorkState.ONLY_STUDYING);
        return people.saveAndFlush(person);
    }

    private Register saveRegistration(String cpf, Course target, StatusRegister status, LocalDateTime date) {
        return registers.saveAndFlush(new Register(savePerson(cpf), target, date, status));
    }

    private Register reload(Register registration) {
        return registers.findById(registration.getId()).orElseThrow();
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "The transaction did not reach its synchronization point");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
