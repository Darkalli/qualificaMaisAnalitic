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
import java.sql.Time;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ClassTimeStatusMigrationTests {
    @Autowired private DataSource dataSource;
    private JdbcTemplate jdbc;
    private String schema;
    private String prefix;

    @BeforeEach
    void createV2Schema() {
        schema = "class_time_test_" + UUID.randomUUID().toString().replace("-", "");
        prefix = "\"" + schema + "\".";
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("2").load().migrate();
        jdbc.update("insert into " + prefix + "course (id, name) values (701, 'Curso A')");
        jdbc.update("insert into " + prefix + "course_class (id, course_id, class_day, session, start, finish) "
                + "values (801, 701, '2026-10-01', 'Manhã', '2026-10-01 08:00:00', '2026-10-01 10:00:00')");
    }

    @AfterEach
    void removeTestSchema() { jdbc.execute("drop schema if exists \"" + schema + "\" cascade"); }

    @Test
    void convertsTimesAndInitializesStatusWithoutChangingClassIdCourseOrDay() {
        var flyway = latest();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertEquals(701L, jdbc.queryForObject("select course_id from " + prefix + "course_class where id = 801", Long.class));
        assertEquals("2026-10-01", jdbc.queryForObject("select class_day from " + prefix + "course_class", java.sql.Date.class).toString());
        assertEquals(Time.valueOf("08:00:00"), jdbc.queryForObject("select start from " + prefix + "course_class", Time.class));
        assertEquals(Time.valueOf("10:00:00"), jdbc.queryForObject("select finish from " + prefix + "course_class", Time.class));
        assertEquals("ACTIVE", jdbc.queryForObject("select status_class from " + prefix + "course_class", String.class));
        assertEquals("3", flyway.info().current().getVersion().getVersion());
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"start = '11:00:00'", "finish = '08:00:00'", "status_class = null", "status_class = 'UNKNOWN'"})
    void databaseRejectsInvalidSchedulesAndStatuses(String change) {
        latest().migrate();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update " + prefix + "course_class set " + change));
        assertEquals("ACTIVE", jdbc.queryForObject("select status_class from " + prefix + "course_class", String.class));
        assertEquals(Time.valueOf("08:00:00"), jdbc.queryForObject("select start from " + prefix + "course_class", Time.class));
    }

    private Flyway latest() { return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load(); }
}
