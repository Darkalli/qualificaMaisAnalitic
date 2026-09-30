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
class PresenceClassMigrationTests {
    @Autowired private DataSource dataSource;
    private JdbcTemplate jdbc;
    private String schema;
    private String prefix;

    @BeforeEach
    void prepareLegacySchema() {
        schema = "presence_migration_test_" + UUID.randomUUID().toString().replace("-", "");
        prefix = "\"" + schema + "\".";
        jdbc = new JdbcTemplate(dataSource);
        flyway("4").migrate();
        jdbc.update("insert into " + prefix + "address (id, number, street, neighborhood) "
                + "values (501, 42, 'Rua Exemplo', 'Centro')");
        jdbc.update("insert into " + prefix + "person (id, full_name, cpf, email, personal_phone, "
                + "personal_phone_has_whatsapp, address_id, gender, education, work_state) "
                + "values (601, 'Pessoa Exemplo', '01234567890', 'pessoa@example.com', "
                + "'11999990000', false, 501, 'FEMALE', 'HIGH_SCHOOL_COMPLETE', 'ONLY_STUDYING')");
        jdbc.update("insert into " + prefix + "course (id, name) values (701, 'Curso A'), (702, 'Curso B')");
        jdbc.update("insert into " + prefix + "course_class (id, course_id, class_day) "
                + "values (801, 701, '2026-10-01'), (802, 702, '2026-10-01'), (803, 701, '2026-10-02')");
        jdbc.update("insert into " + prefix + "presence (id, person_id, course_id, date, status) "
                + "values (901, 601, 701, '2026-10-01', 'PRESENT'), "
                + "(902, 601, 702, '2026-10-01', 'ABSENT'), (903, 601, 701, '2026-10-02', 'JUSTIFIED')");
    }

    @AfterEach
    void removeOnlyThisTestsSchema() {
        jdbc.execute("drop schema if exists \"" + schema + "\" cascade");
    }

    @Test
    void preservesAttendanceAndLinksTheExactClassBeforeRemovingLegacyDate() {
        assertEquals(1, flyway("5").migrate().migrationsExecuted);
        for (long id : new long[]{901, 902, 903}) {
            assertEquals(id - 100, jdbc.queryForObject("select course_class_id from " + prefix
                    + "presence where id = ?", Long.class, id));
            assertEquals(601L, jdbc.queryForObject("select person_id from " + prefix
                    + "presence where id = ?", Long.class, id));
        }
        assertEquals("PRESENT", jdbc.queryForObject("select status from " + prefix + "presence where id = 901", String.class));
        assertEquals("ABSENT", jdbc.queryForObject("select status from " + prefix + "presence where id = 902", String.class));
        assertEquals("JUSTIFIED", jdbc.queryForObject("select status from " + prefix + "presence where id = 903", String.class));
        assertEquals(702L, jdbc.queryForObject("select course_id from " + prefix + "presence where id = 902", Long.class));
        assertEquals(LocalDate.of(2026, 10, 2), jdbc.queryForObject("select class_day from " + prefix
                + "course_class where id = 803", LocalDate.class));
        assertEquals(0, columnCount("date"));
        assertEquals(1, columnCount("course_class_id"));
        assertEquals(3, jdbc.queryForObject("select count(*) from " + prefix + "presence", Integer.class));
        assertEquals(0, flyway("5").migrate().migrationsExecuted);
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "missingClass", "nullClass"})
    void enforcesUniqueRequiredClassAndForeignKey(String scenario) {
        flyway("5").migrate();
        Long classId = switch (scenario) {
            case "duplicate" -> 801L;
            case "missingClass" -> 999L;
            default -> null;
        };
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into " + prefix
                + "presence (person_id, course_id, course_class_id, status) values (601, 701, ?, 'ABSENT')", classId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingClass", "nullDate", "ambiguousClass", "duplicatePresence"})
    void refusesInvalidLegacyDataBeforeChangingColumnsOrAttendance(String scenario) {
        switch (scenario) {
            case "missingClass" -> jdbc.update("delete from " + prefix + "course_class where id = 801");
            case "nullDate" -> jdbc.update("update " + prefix + "presence set date = null where id = 901");
            case "ambiguousClass" -> jdbc.update("insert into " + prefix
                    + "course_class (id, course_id, class_day) values (804, 701, '2026-10-01')");
            case "duplicatePresence" -> jdbc.update("insert into " + prefix
                    + "presence (id, person_id, course_id, date, status) values (904, 601, 701, '2026-10-01', 'ABSENT')");
            default -> throw new IllegalArgumentException(scenario);
        }
        var original = jdbc.queryForList("select * from " + prefix + "presence order by id");

        var failure = assertThrows(FlywayException.class, () -> flyway("5").migrate());

        assertTrue(failure.getMessage().contains(scenario.equals("duplicatePresence")
                ? "presenças duplicadas" : "correspondência única de aula"));
        assertEquals(1, columnCount("date"));
        assertEquals(0, columnCount("course_class_id"));
        assertEquals(original, jdbc.queryForList("select * from " + prefix + "presence order by id"));
    }

    private Flyway flyway(String version) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target(version).load();
    }

    private int columnCount(String column) {
        return jdbc.queryForObject("select count(*) from information_schema.columns "
                + "where lower(table_schema) = ? and lower(table_name) = 'presence' and lower(column_name) = ?",
                Integer.class, schema, column);
    }
}
