package com.example.qualificamaisanalitic;

import com.sheets.*;
import com.entities.Register;
import com.entities.Course;
import com.repositories.CourseRepository;
import jakarta.persistence.EntityNotFoundException;
import com.enums.Disabilities;
import com.repositories.RegisterRepository;
import com.repositories.PersonRepository;
import com.sheets.services.RegisterPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RegisterPersistenceTests {
    @Autowired private RegisterPersistenceService persistence;
    @Autowired private RegisterRepository repository;
    @Autowired private PersonRepository people;
    @Autowired private CourseRepository courses;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    private Long firstCourseId;
    private Long secondCourseId;

    @BeforeEach
    void clearTestDatabase() {
        jdbc.update("delete from presence");
        jdbc.update("delete from course_class");
        jdbc.update("delete from person_disabilities");
        jdbc.update("delete from register");
        jdbc.update("delete from person");
        jdbc.update("delete from address");
        jdbc.update("delete from course");
        firstCourseId = courses.saveAndFlush(new Course("Informática", "Descrição original", null, null)).getId();
        secondCourseId = courses.saveAndFlush(new Course("Inglês", null, null, null)).getId();
    }

    @Test
    void savesCompleteRegistrationWithGeneratedIdsAndReloadsIt() {
        var incoming = incoming("012.345.678-90");
        var result = persistence.persist(collection(incoming));
        assertEquals(1, result.inserted());
        assertNotNull(incoming.getId());
        assertNotNull(incoming.getPerson().getId());
        assertNotNull(incoming.getPerson().getAddress().getId());
        transactions.executeWithoutResult(status -> {
            var saved = repository.findByPerson_CpfAndCourseOfInterest_Id("01234567890", firstCourseId).orElseThrow();
            assertEquals("Pessoa Exemplo", saved.getPerson().getFullName());
            assertEquals("pessoa@example.com", saved.getPerson().getEmail());
            assertEquals("11999990000", saved.getPerson().getPersonalPhone());
            assertFalse(saved.getPerson().getPersonalPhoneHasWhatsapp());
            assertNull(saved.getPerson().getSocialName());
            assertNull(saved.getPerson().getFamilyPhone());
            assertEquals("Rua Exemplo", saved.getPerson().getAddress().getStreet());
            assertEquals(42, saved.getPerson().getAddress().getNumber());
            assertEquals("Centro", saved.getPerson().getAddress().getNeighborhood());
            assertEquals(incoming.getPerson().getGender(), saved.getPerson().getGender());
            assertEquals(incoming.getPerson().getEducation(), saved.getPerson().getEducation());
            assertEquals(incoming.getPerson().getWorkState(), saved.getPerson().getWorkState());
            assertEquals(incoming.getRegisterDate(), saved.getRegisterDate());
            assertEquals(firstCourseId, saved.getCourseOfInterest().getId());
            assertEquals(Set.of(Disabilities.HEARING, Disabilities.VISUAL), saved.getPerson().getDisabilities());
        });
        assertEquals("FEMALE", jdbc.queryForObject("select gender from person", String.class));
    }

    @Test
    void repeatedAndReorderedImportsDoNotCreateRegistrationsOrOrphanAddresses() {
        var first = persistence.persist(collection(incoming("012.345.678-90"),
                incoming("12345678901"), incoming("01234567890")));
        assertEquals(2, first.inserted());
        assertEquals(1, first.unchanged());
        var next = persistence.persist(collection(incoming("12345678901"),
                incoming("01234567890")));
        assertEquals(0, next.inserted());
        assertEquals(2, next.unchanged());
        assertEquals(2, repository.count());
        assertEquals(2, people.count());
        assertEquals(2, jdbc.queryForObject("select count(*) from address", Integer.class));
    }

    @Test
    void reportsChangesWithoutOverwritingSavedData() {
        persistence.persist(collection(incoming("01234567890")));
        var changed = incoming("01234567890");
        changed.getPerson().setEmail("correcao@example.com");
        changed.getPerson().getAddress().setStreet("Outra rua");
        changed.getPerson().setDisabilities(Set.of(Disabilities.MOTOR));
        var result = persistence.persist(collection(changed));
        assertEquals(0, result.inserted());
        assertEquals(0, result.unchanged());
        assertEquals(1, result.conflicts().size());
        assertEquals(List.of("email", "address.street", "disabilities"), result.conflicts().getFirst().fields());
        assertEquals("pessoa@example.com", people.findByCpf("01234567890").orElseThrow().getEmail());
        assertEquals("Rua Exemplo", jdbc.queryForObject("select street from address", String.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from address", Integer.class));
    }

    @Test
    void preservesErrorsAndEmptyRowsWhileSavingValidRows() {
        var errors = List.of(new SheetImportResult.RowError(7, "CPF inválido"));
        var result = persistence.persist(new SheetImportResult(
                List.of(incoming("01234567890")), errors, 2));
        assertEquals(1, result.inserted());
        assertEquals(errors, result.errors());
        assertEquals(2, result.ignoredRows());
    }

    @Test
    void databaseFailureRollsBackTheWholeBatchIncludingAddressesAndDisabilities() {
        var invalid = incoming("12345678901");
        invalid.getPerson().getAddress().setNumber(-1);
        assertThrows(DataIntegrityViolationException.class, () -> persistence.persist(
                collection(incoming("01234567890"), invalid)));
        assertEquals(0, repository.count());
        assertEquals(0, people.count());
        assertEquals(0, jdbc.queryForObject("select count(*) from address", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from person_disabilities", Integer.class));
        assertEquals(1, persistence.persist(collection(incoming("01234567890"))).inserted());
    }

    @Test
    void databaseEnforcesCpfUniquenessEvenWhenBypassingImportService(CapturedOutput output) {
        persistence.persist(collection(incoming("01234567890")));
        assertThrows(DataIntegrityViolationException.class,
                () -> people.saveAndFlush(PersonTestData.person("01234567890")));
        assertEquals(1, repository.count());
        assertEquals(1, people.count());
        assertEquals(1, jdbc.queryForObject("select count(*) from address", Integer.class));
        assertFalse(output.getAll().contains("01234567890"), "Erros SQL não devem expor o CPF no log.");
    }

    @Test
    void rejectsUnnormalizedCpfAndExistingEntityIds() {
        var invalid = incoming("01234567890");
        invalid.getPerson().setCpf("012.345.678-90");
        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(invalid)));
        var identified = incoming("01234567890");
        identified.setId(99L);
        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(identified)));
        var identifiedPerson = incoming("01234567890");
        identifiedPerson.getPerson().setId(99L);
        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(identifiedPerson)));
        var identifiedAddress = incoming("01234567890");
        identifiedAddress.getPerson().getAddress().setId(99L);
        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(identifiedAddress)));
        assertEquals(0, repository.count());
        assertEquals(0, people.count());
    }

    @Test
    void reusesOnePersonAcrossDifferentCoursesAndReimportsWithoutDuplicates() {
        var first = incoming("01234567890");
        var second = incoming("01234567890");
        second.setCourseOfInterest(courseReference(secondCourseId));
        var result = persistence.persist(collection(first, second));
        assertEquals(2, result.inserted());
        assertTrue(result.conflicts().isEmpty());
        assertEquals(first.getPerson().getId(), second.getPerson().getId());
        assertEquals(1, people.count());
        assertEquals(2, repository.count());
        assertEquals(1, jdbc.queryForObject("select count(*) from address", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from person_disabilities", Integer.class));

        var repeated = incoming("01234567890");
        repeated.setCourseOfInterest(courseReference(secondCourseId));
        var reimport = persistence.persist(collection(repeated, incoming("01234567890")));
        assertEquals(0, reimport.inserted());
        assertEquals(2, reimport.unchanged());
        assertTrue(reimport.conflicts().isEmpty());
        assertEquals(1, people.count());
        assertEquals(2, repository.count());
    }

    @Test
    void newCoursePreservesTheExistingPersonAndReportsPersonalDataDifferences() {
        persistence.persist(collection(incoming("01234567890")));
        var incoming = incoming("01234567890");
        incoming.setCourseOfInterest(courseReference(secondCourseId));
        incoming.getPerson().setEmail("alterado@example.com");
        var result = persistence.persist(collection(incoming));
        assertEquals(1, result.inserted());
        assertEquals(1, result.conflicts().size());
        assertEquals(incoming.getId(), result.conflicts().getFirst().registerId());
        assertEquals(List.of("email"), result.conflicts().getFirst().fields());
        assertEquals("pessoa@example.com", people.findByCpf("01234567890").orElseThrow().getEmail());
        assertEquals(1, people.count());
        assertEquals(2, repository.count());
    }

    @Test
    void changedDateDoesNotCreateAnotherRegistrationForTheSameCourse() {
        var original = incoming("01234567890");
        persistence.persist(collection(original));
        var changed = incoming("01234567890");
        changed.setRegisterDate(original.getRegisterDate().plusDays(1));
        var result = persistence.persist(collection(changed));
        assertEquals(0, result.inserted());
        assertEquals(List.of("registerDate"), result.conflicts().getFirst().fields());
        assertEquals(original.getRegisterDate(), repository.findById(original.getId()).orElseThrow().getRegisterDate());
        assertEquals(1, repository.count());
    }

    @Test
    void databaseRejectsDuplicateCourseForTheSamePerson() {
        var original = incoming("01234567890");
        persistence.persist(collection(original));
        var duplicate = new Register(original.getPerson(), original.getCourseOfInterest(), original.getRegisterDate());
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(duplicate));
        assertEquals(1, people.count());
        assertEquals(1, repository.count());
    }

    @Test
    void rejectsMissingPersonOrAddressWithoutSavingPartialData() {
        var noPerson = incoming("01234567890");
        noPerson.setPerson(null);
        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(noPerson)));
        var noAddress = incoming("01234567890");
        noAddress.getPerson().setAddress(null);
        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(noAddress)));
        assertEquals(0, people.count());
        assertEquals(0, repository.count());
    }

    @Test
    void courseRenameDoesNotCreateDuplicateOrOverwriteCatalog() {
        var original = incoming("01234567890");
        persistence.persist(collection(original));
        var course = courses.findById(firstCourseId).orElseThrow();
        course.setName("Informática básica");
        courses.saveAndFlush(course);
        var repeated = incoming("01234567890");
        repeated.getCourseOfInterest().setName("Nome recebido que não deve ser salvo");
        var result = persistence.persist(collection(repeated));
        assertEquals(1, result.unchanged());
        assertEquals(0, result.inserted());
        assertTrue(result.conflicts().isEmpty());
        assertEquals(1, repository.count());
        assertEquals("Informática básica", courses.findById(firstCourseId).orElseThrow().getName());
        assertEquals("Descrição original", courses.findById(firstCourseId).orElseThrow().getDescription());
    }

    @Test
    void coursesWithSameNameRemainDifferentById() {
        var otherCourse = courses.findById(secondCourseId).orElseThrow();
        otherCourse.setName("Informática");
        courses.saveAndFlush(otherCourse);
        var first = incoming("01234567890");
        var second = incoming("01234567890");
        second.setCourseOfInterest(courseReference(secondCourseId));
        assertEquals(2, persistence.persist(collection(first, second)).inserted());
        assertEquals(1, people.count());
        assertEquals(2, repository.count());
    }

    @Test
    void unknownCourseRollsBackBatchWithoutCreatingCoursesOrPeople() {
        var unknown = incoming("12345678901");
        unknown.setCourseOfInterest(courseReference(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class,
                () -> persistence.persist(collection(incoming("01234567890"), unknown)));
        assertEquals(0, repository.count());
        assertEquals(0, people.count());
        assertEquals(0, jdbc.queryForObject("select count(*) from address", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from person_disabilities", Integer.class));
        assertEquals(2, courses.count());
    }

    @Test
    void rejectsMissingOrUnidentifiedCourseWithoutSaving() {
        var row = incoming("01234567890");
        row.setCourseOfInterest(null);
        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(row)));
        row.setCourseOfInterest(new Course());
        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(row)));
        row.getCourseOfInterest().setId(0L);
        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(row)));
        assertEquals(0, repository.count());
        assertEquals(0, people.count());
    }

    @Test
    void databaseEnforcesRequiredAndExistingCourseWhenBypassingImport() {
        var original = incoming("01234567890");
        persistence.persist(collection(original));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into register (person_id, course_id, register_date) values (?, ?, ?)",
                original.getPerson().getId(), Long.MAX_VALUE, original.getRegisterDate()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into register (person_id, register_date) values (?, ?)",
                original.getPerson().getId(), original.getRegisterDate()));
        assertEquals(1, repository.count());
    }

    private Register incoming(String cpf) {
        return RegisterTestData.register(cpf, firstCourseId);
    }

    private Course courseReference(Long id) {
        var course = new Course();
        course.setId(id);
        return course;
    }

    private SheetImportResult collection(Register... registers) {
        return new SheetImportResult(List.of(registers), List.of(), 0);
    }
}
