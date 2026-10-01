package com.example.qualificamaisanalitic;

import com.dtos.registerDtos.AddRegisterDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Register;
import com.repositories.CourseClassRepository;
import com.repositories.CourseRepository;
import com.repositories.PersonRepository;
import com.repositories.RegisterRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Sem transação do teste: cada chamada confirma ou reverte sua própria transação. */
@SpringBootTest
@ActiveProfiles("test")
class RegisterScheduleImportTests {
    private static final String CPF = "01234567890";
    private static final String OTHER_CPF = "12345678901";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    @Autowired private RegisterPersistenceService persistence;
    @Autowired private RegisterService directRegistration;
    @Autowired private RegisterRepository registrations;
    @Autowired private PersonRepository people;
    @Autowired private CourseRepository courses;
    @Autowired private CourseClassRepository classes;
    @Autowired private JdbcTemplate jdbc;
    private Course first;
    private Course second;

    @BeforeEach
    void prepareCatalog() {
        clearDatabase();
        first = courses.saveAndFlush(new Course("Curso A", null, DAY, DAY.plusMonths(1)));
        second = courses.saveAndFlush(new Course("Curso B", null, DAY, DAY.plusMonths(1)));
        addClass(first, DAY, 8, 10);
    }

    @AfterEach
    void clearDatabase() {
        for (String table : List.of("presence", "register", "course_class", "person_disabilities", "person", "address", "course")) {
            jdbc.update("delete from " + table);
        }
    }

    @ParameterizedTest(name = "existing 08–10, incoming {0}–{1}, offset {2}: conflict={3}")
    @CsvSource({
            "7, 9, 0, true", "9, 11, 0, true", "8, 10, 0, true",
            "7, 11, 0, true", "8, 9, 0, true",
            "6, 8, 0, false", "10, 12, 0, false", "11, 12, 0, false",
            "8, 10, 1, false", "8, 10, -1, false"
    })
    void checksPersistedClassSchedulesWhenSheetContainsOnlyCourseIds(int start, int finish, int offset, boolean conflict) {
        addClass(second, DAY.plusDays(offset), start, finish);
        var original = incoming(CPF, first);
        persistence.persist(collection(original));
        var candidate = incoming(CPF, second);
        // O mapper não conhece as aulas: o serviço precisa carregar o curso pelo ID.
        assertTrue(candidate.getCourseOfInterest().getCourseClass().isEmpty());

        if (conflict) {
            assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(candidate)));
            assertCounts(1, 1);
            assertTrue(registrations.findByPerson_CpfAndCourseOfInterest_Id(CPF, second.getId()).isEmpty());
        } else {
            var result = persistence.persist(collection(candidate));
            assertEquals(1, result.inserted());
            assertEquals(0, result.unchanged());
            assertTrue(result.conflicts().isEmpty());
            assertEquals(original.getPerson().getId(), candidate.getPerson().getId());
            assertCounts(2, 1);
        }
        assertTrue(registrations.findById(original.getId()).isPresent());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void conflictWithinOneBatchRollsBackAllRowsRegardlessOfOrder(boolean reverse) {
        addClass(second, DAY, 9, 11);
        var firstRow = incoming(CPF, reverse ? second : first);
        var conflictingRow = incoming(CPF, reverse ? first : second);

        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(
                incoming(OTHER_CPF, first), firstRow, conflictingRow)));

        assertCounts(0, 0);
        assertEquals(2, courses.count());
        assertEquals(2, classes.count());
        // Uma falha não impede uma tentativa posterior válida, feita com objetos novos.
        assertEquals(1, persistence.persist(collection(incoming(CPF, first))).inserted());
        assertCounts(1, 1);
    }

    @Test
    void conflictRollsBackEarlierNewPersonAndPreservesPreviouslyCommittedData() {
        addClass(second, DAY, 9, 11);
        var original = incoming(CPF, first);
        persistence.persist(collection(original));
        var conflicting = incoming(CPF, second);
        conflicting.getPerson().setEmail("alterado@example.com");
        conflicting.getPerson().getAddress().setStreet("Rua recebida na planilha");
        conflicting.getPerson().getDisabilities().clear();

        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(
                incoming(OTHER_CPF, first), conflicting)));

        assertCounts(1, 1);
        assertTrue(people.findByCpf(OTHER_CPF).isEmpty());
        assertEquals(original.getId(), registrations.findByPerson_CpfAndCourseOfInterest_Id(CPF, first.getId()).orElseThrow().getId());
        assertEquals("pessoa@example.com", people.findByCpf(CPF).orElseThrow().getEmail());
        assertEquals("Rua Exemplo", jdbc.queryForObject("select street from address", String.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from person_disabilities where disability in ('HEARING', 'VISUAL')", Integer.class));
    }

    @Test
    void reimportOfScheduledCourseIsIdempotentAndStillReportsPersonalDifferences() {
        var original = incoming(CPF, first);
        persistence.persist(collection(original));

        var repeated = persistence.persist(collection(incoming("012.345.678-90", first)));
        assertEquals(0, repeated.inserted());
        assertEquals(1, repeated.unchanged());
        assertTrue(repeated.conflicts().isEmpty());

        var changed = incoming(CPF, first);
        changed.getPerson().setEmail("alterado@example.com");
        var report = persistence.persist(collection(changed));
        assertEquals(0, report.inserted());
        assertEquals(0, report.unchanged());
        assertEquals(1, report.conflicts().size());
        assertEquals(original.getId(), report.conflicts().getFirst().registerId());
        assertEquals(List.of("email"), report.conflicts().getFirst().fields());
        assertEquals("pessoa@example.com", people.findByCpf(CPF).orElseThrow().getEmail());
        assertCounts(1, 1);
    }

    @Test
    void touchingSchedulesCanBeImportedTogetherAndReorderedWithoutDuplicates() {
        addClass(second, DAY, 10, 12);
        var result = persistence.persist(collection(incoming(CPF, first), incoming(CPF, second), incoming(CPF, first)));
        assertEquals(2, result.inserted());
        assertEquals(1, result.unchanged());
        assertTrue(result.conflicts().isEmpty());

        var repeated = persistence.persist(collection(incoming(CPF, second), incoming(CPF, first)));
        assertEquals(0, repeated.inserted());
        assertEquals(2, repeated.unchanged());
        assertTrue(repeated.conflicts().isEmpty());
        assertCounts(2, 1);
    }

    @Test
    void overlappingSchedulesForDifferentPeopleAreAllowed() {
        addClass(second, DAY, 9, 11);

        var result = persistence.persist(collection(incoming(CPF, first), incoming(OTHER_CPF, second)));

        assertEquals(2, result.inserted());
        assertTrue(result.conflicts().isEmpty());
        assertTrue(registrations.findByPerson_CpfAndCourseOfInterest_Id(CPF, first.getId()).isPresent());
        assertTrue(registrations.findByPerson_CpfAndCourseOfInterest_Id(OTHER_CPF, second.getId()).isPresent());
        assertCounts(2, 2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void directRegistrationAndImportRejectEachOthersConflictingRegistrations(boolean directFirst) {
        addClass(second, DAY, 9, 11);
        if (directFirst) {
            people.saveAndFlush(PersonTestData.person(CPF));
            directRegistration.addRegister(new AddRegisterDto("012.345.678-90", first.getId(), DAY));
            assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(incoming(CPF, second))));
        } else {
            persistence.persist(collection(incoming(CPF, first)));
            assertThrows(IllegalArgumentException.class,
                    () -> directRegistration.addRegister(new AddRegisterDto("012.345.678-90", second.getId(), DAY)));
        }
        assertCounts(1, 1);
        assertTrue(registrations.findByPerson_CpfAndCourseOfInterest_Id(CPF, second.getId()).isEmpty());
    }

    @Test
    void checksAllClassesIncludingAConflictOnALaterDay() {
        addClass(first, DAY.plusDays(1), 14, 16);
        addClass(second, DAY, 10, 12);
        addClass(second, DAY.plusDays(1), 15, 17);
        persistence.persist(collection(incoming(CPF, first)));

        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(incoming(CPF, second))));

        assertCounts(1, 1);
    }

    private void addClass(Course course, LocalDate day, int start, int finish) {
        classes.saveAndFlush(new CourseClass(day, "Sessão", day.atTime(start, 0), day.atTime(finish, 0), course));
    }

    private Register incoming(String cpf, Course course) {
        return RegisterTestData.register(cpf, course.getId());
    }

    private SheetImportResult collection(Register... rows) {
        return new SheetImportResult(List.of(rows), List.of(), 0);
    }

    private void assertCounts(int registerCount, int personCount) {
        assertEquals(registerCount, registrations.count());
        assertEquals(personCount, people.count());
        assertEquals(personCount, jdbc.queryForObject("select count(*) from address", Integer.class));
        assertEquals(personCount * 2, jdbc.queryForObject("select count(*) from person_disabilities", Integer.class));
    }
}
