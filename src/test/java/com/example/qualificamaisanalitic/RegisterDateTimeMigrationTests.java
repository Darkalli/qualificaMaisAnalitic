package com.example.qualificamaisanalitic;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RegisterDateTimeMigrationTests {
    private HikariDataSource database;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createExistingDatabase() {
        database = new HikariDataSource();
        database.setJdbcUrl("jdbc:h2:mem:register_datetime_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        database.setUsername("sa");
        database.setPassword("");
        jdbc = new JdbcTemplate(database);
        flyway("8").migrate();
        jdbc.update("insert into address(id, number, street, neighborhood) values(700, 1, 'Example', 'Center')");
        jdbc.update("insert into person(id, full_name, cpf, email, personal_phone, personal_phone_has_whatsapp, "
                + "address_id, gender, education, work_state) values(700, 'Example', '01234567890', "
                + "'example@example.com', '55999999999', false, 700, 'OTHER', 'HIGH_SCHOOL_COMPLETE', 'ONLY_STUDYING')");
        jdbc.update("insert into course(id, name) values(700, 'Existing course')");
        jdbc.update("insert into register(id, person_id, course_id, register_date) "
                + "values(700, 700, 700, DATE '2026-11-25')");
    }

    @AfterEach
    void closeDatabase() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void upgradesDateToTimestampWithoutLosingExistingRegistration() {
        assertEquals(1, flyway("9").migrate().migrationsExecuted);

        assertEquals(LocalDate.of(2026, 11, 25).atStartOfDay(), registrationDate());
        assertEquals(700L, jdbc.queryForObject("select person_id from register where id=700", Long.class));
        assertEquals(700L, jdbc.queryForObject("select course_id from register where id=700", Long.class));
        assertEquals("ACTIVE", jdbc.queryForObject("select status from register where id=700", String.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from register", Integer.class));
    }

    @Test
    void storesHoursMinutesSecondsAndMicrosecondsAfterUpgrade() {
        flyway("9").migrate();
        LocalDateTime timestamp = LocalDateTime.of(2026, 11, 25, 15, 30, 45, 123456000);

        jdbc.update("update register set register_date=? where id=700", Timestamp.valueOf(timestamp));

        assertEquals(timestamp, registrationDate());
    }

    @Test
    void keepsNotNullConstraintAndDoesNotReapplyMigration() {
        Flyway migration = flyway("9");
        migration.migrate();

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update register set register_date=null where id=700"));
        assertEquals(0, migration.migrate().migrationsExecuted);
        migration.validate();
        assertEquals(LocalDate.of(2026, 11, 25).atStartOfDay(), registrationDate());
    }

    private LocalDateTime registrationDate() {
        Timestamp timestamp = jdbc.queryForObject("select register_date from register where id=700", Timestamp.class);
        assertNotNull(timestamp);
        return timestamp.toLocalDateTime();
    }

    private Flyway flyway(String version) {
        return Flyway.configure().dataSource(database).target(version).load();
    }
}
