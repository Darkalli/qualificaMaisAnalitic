package com.example.qualificamaisanalitic;

import com.entities.Register;
import com.enums.StatusRegister;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;

import java.util.UUID;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

/** A real V4 database containing history is upgraded, independently of the application context. */
class RegisterStatusMigrationTests {
    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private String schema;

    @BeforeEach
    void existingV4DatabaseWithAttendance() throws Exception {
        schema = "register_status_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getProperty("test.db.url", "jdbc:h2:mem:status_" + schema + ";DB_CLOSE_DELAY=-1");
        String username = System.getProperty("test.db.username", "sa");
        String password = System.getProperty("test.db.password", "");
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            statement.execute("create schema \"" + schema + "\"");
        }
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setConnectionInitSql("SET SCHEMA '" + schema + "'");
        jdbc = new JdbcTemplate(dataSource);
        flyway("4").migrate();
        jdbc.update("insert into address(id, number, street, neighborhood) values(700, 1, 'Example', 'Center')");
        jdbc.update("insert into person(id, full_name, cpf, email, personal_phone, personal_phone_has_whatsapp, address_id, gender, education, work_state) values(700, 'Example', '01234567890', 'example@example.com', '11999990000', false, 700, 'FEMALE', 'HIGH_SCHOOL', 'STUDENT')");
        jdbc.update("insert into course(id, name) values(700, 'Existing course')");
        jdbc.update("insert into register(id, person_id, course_id, register_date) values(700, 700, 700, DATE '2026-10-01')");
        jdbc.update("insert into course_class(id, class_day, session, start, finish, course_id) values(700, DATE '2026-10-01', 'Morning', TIME '08:00:00', TIME '10:00:00', 700)");
        jdbc.update("insert into presence(id, person_id, course_id, course_class_id, status) values(700, 700, 700, 700, 'PRESENT')");
    }

    @AfterEach
    void closeDatabase() {
        jdbc.execute("drop schema if exists \"" + schema + "\" cascade");
        dataSource.close();
    }

    @Test
    void upgradesV4BackfillsActivePreservesHistoryAndDoesNotReapply() {
        var migration = flyway("5");
        assertEquals(1, migration.migrate().migrationsExecuted);
        assertEquals("ACTIVE", jdbc.queryForObject("select status from register where id=700", String.class));
        assertEquals(700L, jdbc.queryForObject("select person_id from register where id=700", Long.class));
        assertEquals(700L, jdbc.queryForObject("select course_id from register where id=700", Long.class));
        assertEquals("2026-10-01", jdbc.queryForObject("select register_date from register where id=700", String.class));
        assertEquals("PRESENT", jdbc.queryForObject("select status from presence where id=700", String.class));
        assertEquals(700L, jdbc.queryForObject("select course_class_id from presence where id=700", Long.class));
        jdbc.update("update register set status='CANCELED' where id=700");
        assertEquals(0, migration.migrate().migrationsExecuted);
        migration.validate();
        assertEquals("CANCELED", jdbc.queryForObject("select status from register where id=700", String.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from presence", Integer.class));
    }

    @Test
    void statusHasStringStorageDefaultNotNullAndRestrictedValuesWithoutLosingKeys() {
        flyway("5").migrate();
        assertEquals("character varying", jdbc.queryForObject("select data_type from information_schema.columns where lower(table_name)='register' and lower(column_name)='status' and lower(table_schema)='" + schema + "'", String.class).toLowerCase(java.util.Locale.ROOT));
        assertEquals(32, jdbc.queryForObject("select character_maximum_length from information_schema.columns where lower(table_name)='register' and lower(column_name)='status' and lower(table_schema)='" + schema + "'", Integer.class));
        jdbc.update("insert into course(id, name) values(701, 'Other')");
        jdbc.update("insert into register(id, person_id, course_id, register_date) values(701, 700, 701, CURRENT_DATE)");
        assertEquals("ACTIVE", jdbc.queryForObject("select status from register where id=701", String.class));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update register set status=null where id=700"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update register set status='UNKNOWN' where id=700"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into register(person_id, course_id, register_date) values(700, 700, CURRENT_DATE)"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into register(person_id, course_id, register_date) values(999, 701, CURRENT_DATE)"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("delete from person where id=700"));
    }

    @Test
    void entityUsesStringEnumAndDefaultsToActive() throws Exception {
        var field = Register.class.getDeclaredField("status");
        var enumerated = field.getAnnotation(Enumerated.class);
        assertNotNull(enumerated, "Status must be persisted by name, never ordinal");
        assertEquals(EnumType.STRING, enumerated.value());
        assertFalse(field.getAnnotation(Column.class).nullable());
        assertEquals(32, field.getAnnotation(Column.class).length());
        assertEquals(StatusRegister.ACTIVE, new Register().getStatus());
    }

    private Flyway flyway(String version) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target(version).load();
    }
}
