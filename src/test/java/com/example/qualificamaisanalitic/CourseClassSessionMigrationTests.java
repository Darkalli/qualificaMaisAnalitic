package com.example.qualificamaisanalitic;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CourseClassSessionMigrationTests {
    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createIsolatedV7Database() {
        dataSource = new SingleConnectionDataSource("jdbc:h2:mem:class_session_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=0", "sa", "", true);
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("7").load().migrate();
        jdbc.update("insert into course (id, name, start, finish, period) values "
                + "(801, 'Curso existente', '2026-10-01', '2026-11-30', 'NIGHT')");
    }

    @AfterEach
    void closeOnlyTheDatabaseCreatedByThisTest() {
        if (dataSource != null) dataSource.destroy();
    }

    @Test
    void normalizesKnownSessionsWithoutInferringFromTimesOrChangingOtherData() {
        String[] legacy = {"MORNING", " afternoon ", "nIgHt", " Manhã ", "tArDe", "NOITE"};
        List<String> expected = List.of("MORNING", "AFTERNOON", "NIGHT", "MORNING", "AFTERNOON", "NIGHT");
        for (int i = 0; i < legacy.length; i++) insertClass(901 + i, legacy[i]);
        var otherData = nonSessionData();
        var column = sessionColumn();
        var course = jdbc.queryForList("select * from course");

        var flyway = latest();
        assertEquals(1, flyway.migrate().migrationsExecuted);

        assertEquals(expected, jdbc.queryForList("select session from course_class order by id", String.class));
        assertEquals(otherData, nonSessionData());
        assertEquals(column, sessionColumn());
        assertEquals(course, jdbc.queryForList("select * from course"));
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "", "   ", "Integral", "08:00", "MANHA"})
    void refusesUnknownLegacySessionsBeforeChangingAnyExistingRow(String unknown) {
        insertClass(901, " manhã ");
        insertClass(902, unknown);
        var before = jdbc.queryForList("select * from course_class order by id");
        var column = sessionColumn();

        assertThrows(FlywayException.class, () -> latest().migrate());

        assertEquals(before, jdbc.queryForList("select * from course_class order by id"));
        assertEquals(column, sessionColumn());
        assertEquals(0, jdbc.queryForObject("select count(*) from \"flyway_schema_history\" "
                + "where \"version\" = '8' and \"success\" = true", Integer.class));
    }

    @Test
    void acceptsOnlyCanonicalEnumStringsAfterUpgrade() {
        insertClass(901, "Manhã");
        latest().migrate();
        for (String period : List.of("MORNING", "AFTERNOON", "NIGHT")) {
            jdbc.update("update course_class set session = ? where id = 901", period);
            assertEquals(period, jdbc.queryForObject("select session from course_class where id = 901", String.class));
        }
        for (String invalid : List.of("UNKNOWN", "Manhã", "morning", " NIGHT ", "")) {
            assertThrows(DataIntegrityViolationException.class,
                    () -> jdbc.update("update course_class set session = ? where id = 901", invalid));
        }
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update course_class set session = null where id = 901"));
        assertThrows(DataIntegrityViolationException.class, () -> insertClass(902, "Tarde"));
    }

    private void insertClass(int id, String session) {
        jdbc.update("insert into course_class (id, class_day, session, start, finish, course_id, status_class) "
                        + "values (?, ?, ?, '18:01:02.123456', '20:03:04.654321', 801, ?)",
                id, java.sql.Date.valueOf(java.time.LocalDate.of(2026, 10, 1).plusDays(id - 901)), session,
                new String[]{"ACTIVE", "CANCELED", "POSTPONED"}[Math.floorMod(id, 3)]);
    }

    private List<Map<String, Object>> nonSessionData() {
        return jdbc.queryForList("select id, class_day, start, finish, course_id, status_class from course_class order by id");
    }

    private List<Map<String, Object>> sessionColumn() {
        return jdbc.queryForList("select data_type, character_maximum_length, is_nullable "
                + "from information_schema.columns where table_name = 'COURSE_CLASS' and column_name = 'SESSION'");
    }

    private Flyway latest() {
        return Flyway.configure().dataSource(dataSource).load();
    }
}
