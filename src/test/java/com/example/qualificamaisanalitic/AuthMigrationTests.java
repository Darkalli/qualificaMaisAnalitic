package com.example.qualificamaisanalitic;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AuthMigrationTests {
    @Autowired private DataSource dataSource;
    private JdbcTemplate jdbc;
    private String schema;
    private String prefix;

    @BeforeEach
    void createExistingV3Database() {
        jdbc = new JdbcTemplate(dataSource);
        schema = "auth_migration_" + UUID.randomUUID().toString().replace("-", "");
        prefix = "\"" + schema + "\".";
        flyway("3").migrate();
        jdbc.update("insert into " + prefix + "course (id, name) values (700, 'Curso existente')");
    }

    @AfterEach
    void clean() {
        jdbc.execute("drop schema if exists \"" + schema + "\" cascade");
    }

    @Test
    void addsAuthenticationWithoutChangingExistingCoursesAndDoesNotReapply() {
        var migration = flyway("4");
        assertEquals(1, migration.migrate().migrationsExecuted);
        assertEquals("Curso existente", jdbc.queryForObject("select name from " + prefix + "course where id = 700", String.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from " + prefix + "app_user", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from " + prefix + "auth_session", Integer.class));
        assertEquals(0, migration.migrate().migrationsExecuted);
        migration.validate();
    }

    @Test
    void databaseEnforcesUsernameRoleTokenUniquenessAndUserReference() {
        flyway("4").migrate();
        jdbc.update("insert into " + prefix + "app_user (id, username, password_hash, role) values (800, 'operator', 'hash', 'AGENT')");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into " + prefix
                + "app_user (username, password_hash, role) values ('operator', 'hash', 'ADMIN')"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into " + prefix
                + "app_user (username, password_hash, role) values ('another', 'hash', 'UNKNOWN')"));
        String insert = "insert into " + prefix + "auth_session (user_id, token_hash, expires_at) values (?, ?, CURRENT_TIMESTAMP)";
        jdbc.update(insert, 800, "a".repeat(64));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insert, 800, "a".repeat(64)));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insert, 999, "b".repeat(64)));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("delete from " + prefix + "app_user where id = 800"));
    }

    private Flyway flyway(String version) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target(version).load();
    }
}
