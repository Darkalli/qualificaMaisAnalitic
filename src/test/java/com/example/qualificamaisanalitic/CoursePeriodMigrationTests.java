package com.example.qualificamaisanalitic;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CoursePeriodMigrationTests {
    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createIsolatedDatabaseWithAnExistingCourse() {
        dataSource = new SingleConnectionDataSource("jdbc:h2:mem:course_period_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=0", "sa", "", true);
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("6").load().migrate();
        jdbc.update("insert into course (id, name, description, start, finish) values "
                + "(701, 'Curso existente', 'Descrição original', '2026-10-01', '2026-11-01')");
    }

    @AfterEach
    void stopOnlyTheInMemoryDatabaseCreatedByThisTest() {
        if (dataSource != null) dataSource.destroy();
    }

    @Test
    void addsPeriodWithoutInventingAValueOrChangingExistingCourseData() {
        var flyway = Flyway.configure().dataSource(dataSource).target("7").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertNull(jdbc.queryForObject("select period from course where id = 701", String.class));
        assertEquals("Curso existente", jdbc.queryForObject("select name from course where id = 701", String.class));
        assertEquals("Descrição original", jdbc.queryForObject("select description from course where id = 701", String.class));
        assertEquals("ACTIVE", jdbc.queryForObject("select status_course from course where id = 701", String.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from course", Integer.class));
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
    }

    @Test
    void acceptsTheFilterPeriodsAndRejectsUnknownValues() {
        Flyway.configure().dataSource(dataSource).target("7").load().migrate();
        for (String period : new String[]{"MORNING", "AFTERNOON", "NIGHT"}) {
            jdbc.update("update course set period = ? where id = 701", period);
            assertEquals(period, jdbc.queryForObject("select period from course where id = 701", String.class));
        }
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update course set period = 'UNKNOWN' where id = 701"));
        jdbc.update("update course set period = null where id = 701");
        assertNull(jdbc.queryForObject("select period from course where id = 701", String.class));
    }
}
