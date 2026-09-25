package com.example.qualificamaisanalitic;

import com.sheets.*;
import com.sheets.entities.Register;
import com.sheets.enums.Disabilities;
import com.sheets.repositories.RegisterRepository;
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
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;

    @BeforeEach
    void clearTestDatabase() {
        jdbc.update("delete from register_disabilities");
        jdbc.update("delete from register");
        jdbc.update("delete from address");
    }

    @Test
    void savesCompleteRegistrationWithGeneratedIdsAndReloadsIt() {
        var incoming = RegisterTestData.register("012.345.678-90");
        var result = persistence.persist(collection(incoming));
        assertEquals(1, result.inserted());
        assertNotNull(incoming.getId());
        assertNotNull(incoming.getAddress().getId());
        transactions.executeWithoutResult(status -> {
            var saved = repository.findByCpf("01234567890").orElseThrow();
            assertEquals("Pessoa Exemplo", saved.getFullName());
            assertEquals("pessoa@example.com", saved.getEmail());
            assertEquals("11999990000", saved.getPersonalPhone());
            assertFalse(saved.getPersonalPhoneHasWhatsapp());
            assertNull(saved.getSocialName());
            assertNull(saved.getFamilyPhone());
            assertEquals("Rua Exemplo", saved.getAddress().getStreet());
            assertEquals(42, saved.getAddress().getNumber());
            assertEquals("Centro", saved.getAddress().getNeighborhood());
            assertEquals(incoming.getGender(), saved.getGender());
            assertEquals(incoming.getEducation(), saved.getEducation());
            assertEquals(incoming.getWorkState(), saved.getWorkState());
            assertEquals(incoming.getRegisterDate(), saved.getRegisterDate());
            assertEquals("Informática", saved.getCourseOfInterest());
            assertEquals(Set.of(Disabilities.HEARING, Disabilities.VISUAL), saved.getDisabilities());
        });
        assertEquals("FEMALE", jdbc.queryForObject("select gender from register", String.class));
    }

    @Test
    void repeatedAndReorderedImportsDoNotCreateRegistrationsOrOrphanAddresses() {
        var first = persistence.persist(collection(RegisterTestData.register("012.345.678-90"),
                RegisterTestData.register("12345678901"), RegisterTestData.register("01234567890")));
        assertEquals(2, first.inserted());
        assertEquals(1, first.unchanged());
        var next = persistence.persist(collection(RegisterTestData.register("12345678901"),
                RegisterTestData.register("01234567890")));
        assertEquals(0, next.inserted());
        assertEquals(2, next.unchanged());
        assertEquals(2, repository.count());
        assertEquals(2, jdbc.queryForObject("select count(*) from address", Integer.class));
    }

    @Test
    void reportsChangesWithoutOverwritingSavedData() {
        persistence.persist(collection(RegisterTestData.register("01234567890")));
        var changed = RegisterTestData.register("01234567890");
        changed.setEmail("correcao@example.com");
        changed.getAddress().setStreet("Outra rua");
        changed.setDisabilities(Set.of(Disabilities.MOTOR));
        var result = persistence.persist(collection(changed));
        assertEquals(0, result.inserted());
        assertEquals(0, result.unchanged());
        assertEquals(1, result.conflicts().size());
        assertEquals(List.of("email", "address.street", "disabilities"), result.conflicts().getFirst().fields());
        assertEquals("pessoa@example.com", repository.findByCpf("01234567890").orElseThrow().getEmail());
        assertEquals("Rua Exemplo", jdbc.queryForObject("select street from address", String.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from address", Integer.class));
    }

    @Test
    void preservesErrorsAndEmptyRowsWhileSavingValidRows() {
        var errors = List.of(new SheetImportResult.RowError(7, "CPF inválido"));
        var result = persistence.persist(new SheetImportResult(
                List.of(RegisterTestData.register("01234567890")), errors, 2));
        assertEquals(1, result.inserted());
        assertEquals(errors, result.errors());
        assertEquals(2, result.ignoredRows());
    }

    @Test
    void databaseFailureRollsBackTheWholeBatchIncludingAddressesAndDisabilities() {
        var invalid = RegisterTestData.register("12345678901");
        invalid.getAddress().setNumber(-1);
        assertThrows(DataIntegrityViolationException.class, () -> persistence.persist(
                collection(RegisterTestData.register("01234567890"), invalid)));
        assertEquals(0, repository.count());
        assertEquals(0, jdbc.queryForObject("select count(*) from address", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from register_disabilities", Integer.class));
        assertEquals(1, persistence.persist(collection(RegisterTestData.register("01234567890"))).inserted());
    }

    @Test
    void databaseEnforcesCpfUniquenessEvenWhenBypassingImportService(CapturedOutput output) {
        persistence.persist(collection(RegisterTestData.register("01234567890")));
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(RegisterTestData.register("01234567890")));
        assertEquals(1, repository.count());
        assertEquals(1, jdbc.queryForObject("select count(*) from address", Integer.class));
        assertFalse(output.getAll().contains("01234567890"), "Erros SQL não devem expor o CPF no log.");
    }

    @Test
    void rejectsUnnormalizedCpfAndExistingEntityIds() {
        var invalid = RegisterTestData.register("01234567890");
        invalid.setCpf("012.345.678-90");
        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(invalid)));
        var identified = RegisterTestData.register("01234567890");
        identified.setId(99L);
        assertThrows(IllegalArgumentException.class, () -> persistence.persist(collection(identified)));
        assertEquals(0, repository.count());
    }

    private SheetImportResult collection(Register... registers) {
        return new SheetImportResult(List.of(registers), List.of(), 0);
    }
}
