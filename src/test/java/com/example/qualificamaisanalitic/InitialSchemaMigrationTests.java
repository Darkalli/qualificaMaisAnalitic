package com.example.qualificamaisanalitic;

import org.flywaydb.core.Flyway;
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
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class InitialSchemaMigrationTests {
    @Autowired private DataSource dataSource;
    private JdbcTemplate jdbc;
    private Flyway flyway;
    private String schema;
    private String prefix;

    @BeforeEach
    void createEmptySchema() {
        schema = "initial_schema_test_" + UUID.randomUUID().toString().replace("-", "");
        prefix = "\"" + schema + "\".";
        jdbc = new JdbcTemplate(dataSource);
        flyway = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
    }

    @AfterEach
    void removeOnlyTheSchemaCreatedByThisTest() {
        jdbc.execute("drop schema if exists \"" + schema + "\" cascade");
    }

    @Test
    void createsOnlyCurrentTablesInOneMigrationAndDoesNotReapplyIt() {
        var tables = Set.copyOf(jdbc.queryForList("select lower(table_name) from information_schema.tables "
                + "where lower(table_schema) = ?", String.class, schema));
        assertEquals(Set.of("address", "person", "person_disabilities", "register", "course",
                "course_class", "presence", "flyway_schema_history"), tables);
        for (String table : tables) {
            if (!table.equals("flyway_schema_history")) {
                assertEquals(0, jdbc.queryForObject("select count(*) from " + prefix + table, Integer.class));
            }
        }
        // O Flyway também registra a criação do schema, sem versão de migração.
        var migrations = Arrays.stream(flyway.info().applied())
                .filter(migration -> migration.getVersion() != null).toArray(org.flywaydb.core.api.MigrationInfo[]::new);
        assertEquals(1, migrations.length);
        assertEquals("1", migrations[0].getVersion().getVersion());
        assertEquals("V1__create_initial_schema.sql", migrations[0].getScript());
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(0, jdbc.queryForObject("select count(*) from information_schema.columns "
                + "where lower(table_schema) = ? and ((lower(table_name) = 'register' "
                + "and lower(column_name) in ('cpf', 'full_name', 'course_of_interest')) "
                + "or (lower(table_name) = 'presence' and lower(column_name) = 'date'))", Integer.class, schema));
    }

    @Test
    void generatesIdsAndStoresTheCompleteCurrentModelFromAnEmptyDatabase() {
        seedPersonAndCourses();
        jdbc.update("insert into " + prefix + "person_disabilities (person_id, disability) values (601, 'HEARING'), (601, 'VISUAL')");
        jdbc.update("insert into " + prefix + "register (person_id, course_id, register_date) values (601, 701, '2026-10-01')");
        jdbc.update("insert into " + prefix + "register (person_id, course_id, register_date) values (601, 702, '2026-10-01')");
        jdbc.update("insert into " + prefix + "course_class (course_id, class_day) values (701, '2026-10-01')");
        long classId = jdbc.queryForObject("select id from " + prefix + "course_class", Long.class);
        jdbc.update("insert into " + prefix + "presence (person_id, course_id, course_class_id, status) values (601, 701, ?, 'PRESENT')", classId);
        assertTrue(classId > 0);
        assertTrue(jdbc.queryForObject("select id from " + prefix + "presence", Long.class) > 0);
        var registrationIds = jdbc.queryForList("select id from " + prefix + "register order by id", Long.class);
        assertEquals(2, registrationIds.size());
        assertTrue(registrationIds.get(0) > 0);
        assertTrue(registrationIds.get(1) > registrationIds.get(0));
        assertEquals(2, jdbc.queryForObject("select count(*) from " + prefix + "person_disabilities", Integer.class));
        assertEquals(classId, jdbc.queryForObject("select course_class_id from " + prefix + "presence", Long.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(701, '2026-10-01')", "(null, '2026-10-02')", "(701, null)", "(999, '2026-10-02')"
    })
    void databaseRejectsDuplicateMissingOrUnknownClassReferences(String values) {
        seedPersonAndCourses();
        jdbc.update("insert into " + prefix + "course_class (course_id, class_day) values (701, '2026-10-01')");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into " + prefix + "course_class (course_id, class_day) values " + values));
        assertEquals(1, jdbc.queryForObject("select count(*) from " + prefix + "course_class", Integer.class));
    }

    @Test
    void databaseAllowsDifferentCoursesOnSameDayAndSameCourseOnDifferentDays() {
        seedPersonAndCourses();
        jdbc.update("insert into " + prefix + "course_class (course_id, class_day) values "
                + "(701, '2026-10-01'), (702, '2026-10-01'), (701, '2026-10-02')");
        assertEquals(3, jdbc.queryForObject("select count(*) from " + prefix + "course_class", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(601, 701, 801)", "(601, 701, null)", "(601, 701, 999)", "(999, 701, 801)", "(601, 999, 801)"
    })
    void databaseEnforcesAttendanceUniquenessAndReferences(String values) {
        seedPersonAndCourses();
        jdbc.update("insert into " + prefix + "course_class (id, course_id, class_day) values (801, 701, '2026-10-01')");
        jdbc.update("insert into " + prefix + "presence (person_id, course_id, course_class_id) values (601, 701, 801)");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into " + prefix + "presence (person_id, course_id, course_class_id) values " + values));
        assertEquals(1, jdbc.queryForObject("select count(*) from " + prefix + "presence", Integer.class));
    }

    private void seedPersonAndCourses() {
        jdbc.update("insert into " + prefix + "address (id, number, street, neighborhood) values (501, 42, 'Rua Exemplo', 'Centro')");
        jdbc.update("insert into " + prefix + "person (id, full_name, cpf, email, personal_phone, "
                + "personal_phone_has_whatsapp, address_id, gender, education, work_state) "
                + "values (601, 'Pessoa Exemplo', '01234567890', 'pessoa@example.com', "
                + "'11999990000', false, 501, 'FEMALE', 'HIGH_SCHOOL_COMPLETE', 'ONLY_STUDYING')");
        jdbc.update("insert into " + prefix + "course (id, name) values (701, 'Curso A'), (702, 'Curso B')");
    }
}
