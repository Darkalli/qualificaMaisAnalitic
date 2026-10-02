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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ClassRequiredFieldsMigrationTests {
    @Autowired private DataSource dataSource;
    private JdbcTemplate jdbc;
    private String schema;
    private String prefix;

    @BeforeEach
    void createV1SchemaWithValidClass() {
        jdbc = new JdbcTemplate(dataSource);
        schema = "required_class_test_" + UUID.randomUUID().toString().replace("-", "");
        prefix = "\"" + schema + "\".";
        assertEquals(1, Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("1").load()
                .migrate().migrationsExecuted);
        jdbc.update("insert into " + prefix + "course (id, name) values (701, 'Curso A')");
        jdbc.update("insert into " + prefix + "course_class (id, course_id, class_day, session, start, finish) "
                + "values (801, 701, '2026-10-01', 'Manhã', '2026-10-01 08:00:00', '2026-10-01 10:00:00')");
    }

    @AfterEach
    void removeOnlyTestSchema() {
        jdbc.execute("drop schema if exists \"" + schema + "\" cascade");
    }

    @Test
    void upgradesV1PreservingClassAndDoesNotReapplyMigration() {
        var before = jdbc.queryForMap("select * from " + prefix + "course_class where id = 801");
        var flyway = latest();

        assertEquals(1, flyway.migrate().migrationsExecuted);

        assertEquals(before, jdbc.queryForMap("select * from " + prefix + "course_class where id = 801"));
        assertEquals(3, jdbc.queryForObject("select count(*) from information_schema.columns "
                + "where lower(table_schema) = ? and lower(table_name) = 'course_class' "
                + "and lower(column_name) in ('session', 'start', 'finish') and is_nullable = 'NO'", Integer.class, schema));
        assertEquals("2", flyway.info().current().getVersion().getVersion());
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"session", "start", "finish"})
    void databaseRejectsNullFieldsOnBothInsertAndUpdate(String field) {
        latest().migrate();
        String session = field.equals("session") ? "null" : "'Manhã'";
        String start = field.equals("start") ? "null" : "'2026-10-02 08:00:00'";
        String finish = field.equals("finish") ? "null" : "'2026-10-02 10:00:00'";
        var before = jdbc.queryForMap("select * from " + prefix + "course_class where id = 801");

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into " + prefix
                + "course_class (course_id, class_day, session, start, finish) values "
                + "(701, '2026-10-02', " + session + ", " + start + ", " + finish + ")"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update " + prefix
                + "course_class set " + field + " = null where id = 801"));

        assertEquals(before, jdbc.queryForMap("select * from " + prefix + "course_class where id = 801"));
        assertEquals(1, jdbc.queryForObject("select count(*) from " + prefix + "course_class", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"session", "start", "finish"})
    void migrationRefusesExistingNullsWithoutInventingValuesOrDeletingClasses(String field) {
        jdbc.update("update " + prefix + "course_class set " + field + " = null where id = 801");
        var before = jdbc.queryForMap("select * from " + prefix + "course_class where id = 801");

        assertThrows(FlywayException.class, () -> latest().migrate());

        assertEquals(before, jdbc.queryForMap("select * from " + prefix + "course_class where id = 801"));
        assertEquals(1, jdbc.queryForObject("select count(*) from " + prefix + "course_class", Integer.class));
    }

    private Flyway latest() {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("2").load();
    }
}
