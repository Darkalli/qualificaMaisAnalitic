package com.example.qualificamaisanalitic;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RegisterCourseMigrationTests {
    @Autowired private DataSource dataSource;
    private JdbcTemplate jdbc;
    private String schema;
    private String prefix;

    @BeforeEach
    void prepareLegacySchema() {
        schema = "course_migration_test_" + UUID.randomUUID().toString().replace("-", "");
        prefix = "\"" + schema + "\".";
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target("3").load().migrate();
        jdbc.update("insert into " + prefix + "address (id, number, street, neighborhood) "
                + "values (501, 42, 'Rua Exemplo', 'Centro')");
        jdbc.update("insert into " + prefix + "person (id, full_name, cpf, email, personal_phone, "
                + "personal_phone_has_whatsapp, address_id, gender, education, work_state) "
                + "values (601, 'Pessoa Exemplo', '01234567890', 'pessoa@example.com', "
                + "'11999990000', false, 501, 'FEMALE', 'HIGH_SCHOOL_COMPLETE', 'ONLY_STUDYING')");
        jdbc.update("insert into " + prefix + "person_disabilities (person_id, disability) values (601, 'HEARING')");
        jdbc.update("insert into " + prefix + "register (id, person_id, course_of_interest, register_date) "
                + "values (901, 601, 'Informática', '2026-09-25')");
    }

    @AfterEach
    void removeOnlyThisTestsSchema() {
        jdbc.execute("drop schema if exists \"" + schema + "\" cascade");
    }

    @Test
    void linksExistingCoursesPreservingRegistrationsAndEnforcingNewConstraints() {
        jdbc.update("insert into " + prefix + "course (id, name, description) "
                + "values (701, 'Informática', 'Descrição original'), (702, 'Inglês', null)");
        jdbc.update("insert into " + prefix + "register (id, person_id, course_of_interest, register_date) "
                + "values (902, 601, 'Inglês', '2026-09-26')");

        assertEquals(1, migrate().migrationsExecuted);

        assertEquals(701L, jdbc.queryForObject("select course_id from " + prefix + "register where id = 901", Long.class));
        assertEquals(702L, jdbc.queryForObject("select course_id from " + prefix + "register where id = 902", Long.class));
        assertEquals(601L, jdbc.queryForObject("select person_id from " + prefix + "register where id = 901", Long.class));
        assertEquals(LocalDate.of(2026, 9, 25), jdbc.queryForObject(
                "select register_date from " + prefix + "register where id = 901", LocalDate.class));
        assertEquals(501L, jdbc.queryForObject("select address_id from " + prefix + "person where id = 601", Long.class));
        assertEquals("HEARING", jdbc.queryForObject("select disability from " + prefix + "person_disabilities", String.class));
        assertEquals("Descrição original", jdbc.queryForObject("select description from " + prefix + "course where id = 701", String.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from " + prefix + "course", Integer.class));
        assertEquals(0, columnCount("course_of_interest"));
        assertEquals(1, columnCount("course_id"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into " + prefix
                + "register (person_id, course_id, register_date) values (601, 701, '2026-09-28')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into " + prefix
                + "register (person_id, course_id, register_date) values (601, 999, '2026-09-28')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into " + prefix
                + "register (person_id, register_date) values (601, '2026-09-28')"));
        assertEquals(0, migrate().migrationsExecuted);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "differentSpelling", "duplicate"})
    void refusesUnresolvedCoursesBeforeChangingLegacyDataOrColumns(String scenario) {
        if (scenario.equals("differentSpelling")) {
            jdbc.update("insert into " + prefix + "course (name) values ('informatica')");
        } else if (scenario.equals("duplicate")) {
            jdbc.update("insert into " + prefix + "course (name) values ('Informática'), ('Informática')");
        }
        int originalCourses = jdbc.queryForObject("select count(*) from " + prefix + "course", Integer.class);

        var failure = assertThrows(FlywayException.class, this::migrate);

        assertTrue(failure.getMessage().contains("correspondência única"));
        assertEquals("Informática", jdbc.queryForObject("select course_of_interest from " + prefix
                + "register where id = 901", String.class));
        assertEquals(1, columnCount("course_of_interest"));
        assertEquals(0, columnCount("course_id"));
        assertEquals(originalCourses, jdbc.queryForObject("select count(*) from " + prefix + "course", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from " + prefix + "person", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from " + prefix + "register", Integer.class));
    }

    private org.flywaydb.core.api.output.MigrateResult migrate() {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("4").load().migrate();
    }

    private int columnCount(String column) {
        return jdbc.queryForObject("select count(*) from information_schema.columns "
                + "where lower(table_schema) = ? and lower(table_name) = 'register' and lower(column_name) = ?",
                Integer.class, schema, column);
    }
}
