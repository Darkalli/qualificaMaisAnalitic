package com.example.qualificamaisanalitic;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CourseCapacityMigrationTests {
    private HikariDataSource database;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createExistingCourseAndRegistration() {
        database = new HikariDataSource();
        database.setJdbcUrl("jdbc:h2:mem:course_capacity_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1");
        database.setUsername("sa");
        database.setPassword("");
        jdbc = new JdbcTemplate(database);
        flyway("9").migrate();
        jdbc.update("insert into course(id, name) values(700, 'Existing course')");
        saveActiveRegistration(700);
    }

    @AfterEach
    void closeDatabase() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void backfillsCapacityWithoutDemotingExistingActiveRegistrations() {
        for (int id = 701; id < 725; id++) {
            saveActiveRegistration(id);
        }

        assertEquals(1, flyway("10").migrate().migrationsExecuted);

        assertEquals(25, jdbc.queryForObject(
                "select maximum_students_count from course where id=700", Integer.class));
        assertEquals(25, jdbc.queryForObject(
                "select count(*) from register where course_id=700 and status='ACTIVE'", Integer.class));
        assertEquals("Existing course", jdbc.queryForObject("select name from course where id=700", String.class));
        assertEquals(LocalDateTime.of(2026, 11, 25, 8, 30, 45), jdbc.queryForObject(
                "select register_date from register where id=700", LocalDateTime.class));
    }

    @Test
    void allowsWaitlistedStatusKeepsCapacityPositiveAndDoesNotReapply() {
        Flyway migration = flyway("10");
        migration.migrate();
        assertEquals(20, jdbc.queryForObject(
                "select maximum_students_count from course where id=700", Integer.class));

        jdbc.update("update register set status='WAITLISTED' where id=700");
        assertEquals("WAITLISTED", jdbc.queryForObject("select status from register where id=700", String.class));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update register set status='UNKNOWN' where id=700"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update course set maximum_students_count=0 where id=700"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update course set maximum_students_count=null where id=700"));
        assertEquals(0, migration.migrate().migrationsExecuted);
        migration.validate();
    }

    private void saveActiveRegistration(int id) {
        jdbc.update("insert into address(id, number, street, neighborhood) values(?, 1, 'Example', 'Center')", id);
        jdbc.update("insert into person(id, full_name, cpf, email, personal_phone, personal_phone_has_whatsapp, "
                        + "address_id, gender, education, work_state) values(?, 'Example', ?, "
                        + "'example@example.com', '55999999999', false, ?, 'OTHER', 'HIGH_SCHOOL_COMPLETE', 'ONLY_STUDYING')",
                id, "%011d".formatted(id), id);
        jdbc.update("insert into register(id, person_id, course_id, register_date) "
                + "values(?, ?, 700, TIMESTAMP '2026-11-25 08:30:45')", id, id);
    }

    private Flyway flyway(String version) {
        return Flyway.configure().dataSource(database).target(version).load();
    }
}
