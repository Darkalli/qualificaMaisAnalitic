package com.example.qualificamaisanalitic;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PersonMigrationTests {
    @Autowired private DataSource dataSource;

    @Test
    void migratesExistingRegistrationsAndKeepsPersonIdsIndependentOfRegistrationIds() {
        String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        String prefix = "\"" + schema + "\".";
        var jdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("1").load().migrate();
            jdbc.update("insert into " + prefix + "address (id, number, street, neighborhood) values (501, 42, 'Rua Exemplo', 'Centro')");
            jdbc.update("insert into " + prefix + "register (id, full_name, social_name, cpf, email, personal_phone, "
                            + "personal_phone_has_whatsapp, family_phone, address_id, gender, education, work_state, "
                            + "course_of_interest, register_date) values (901, ?, ?, ?, ?, ?, ?, ?, 501, ?, ?, ?, ?, ?)",
                    "Pessoa Exemplo", "Nome social", "01234567890", "pessoa@example.com", "11999990000",
                    false, "1133330000", "FEMALE", "HIGH_SCHOOL_COMPLETE", "ONLY_STUDYING",
                    "Informática", LocalDate.of(2026, 9, 25));
            jdbc.update("insert into " + prefix + "register_disabilities (register_id, disability) values (901, 'HEARING'), (901, 'VISUAL')");

            // Este cenário verifica especificamente V1 -> V2; a evolução V3 -> V4 tem teste próprio.
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("2").load().migrate();

            var person = jdbc.queryForMap("select * from " + prefix + "person where cpf = '01234567890'");
            Long personId = ((Number) person.get("id")).longValue();
            assertNotEquals(901L, personId);
            assertEquals("Pessoa Exemplo", person.get("full_name"));
            assertEquals("Nome social", person.get("social_name"));
            assertEquals("pessoa@example.com", person.get("email"));
            assertEquals("11999990000", person.get("personal_phone"));
            assertEquals(false, person.get("personal_phone_has_whatsapp"));
            assertEquals("1133330000", person.get("family_phone"));
            assertEquals(501L, ((Number) person.get("address_id")).longValue());
            assertEquals("FEMALE", person.get("gender"));
            assertEquals("HIGH_SCHOOL_COMPLETE", person.get("education"));
            assertEquals("ONLY_STUDYING", person.get("work_state"));
            assertEquals(personId, jdbc.queryForObject("select person_id from " + prefix + "register where id = 901", Long.class));
            assertEquals("Informática", jdbc.queryForObject("select course_of_interest from " + prefix + "register where id = 901", String.class));
            assertEquals(LocalDate.of(2026, 9, 25), jdbc.queryForObject(
                    "select register_date from " + prefix + "register where id = 901", LocalDate.class));
            assertEquals(Set.of("HEARING", "VISUAL"), Set.copyOf(jdbc.queryForList(
                    "select disability from " + prefix + "person_disabilities where person_id = ?", String.class, personId)));
            assertEquals("Rua Exemplo", jdbc.queryForObject("select street from " + prefix + "address where id = 501", String.class));

            jdbc.update("insert into " + prefix + "register (person_id, course_of_interest, register_date) values (?, ?, ?)",
                    personId, "Inglês", LocalDate.of(2026, 9, 28));
            assertEquals(2, jdbc.queryForObject("select count(*) from " + prefix + "register where person_id = ?", Integer.class, personId));

            jdbc.update("insert into " + prefix + "address (id, number, street, neighborhood) values (502, 10, 'Outra rua', 'Centro')");
            jdbc.update("insert into " + prefix + "person (full_name, cpf, email, personal_phone, personal_phone_has_whatsapp, "
                    + "address_id, gender, education, work_state) values ('Outra pessoa', '12345678901', 'outra@example.com', "
                    + "'11999990001', true, 502, 'FEMALE', 'HIGH_SCHOOL_COMPLETE', 'ONLY_STUDYING')");
            Long nextId = jdbc.queryForObject("select id from " + prefix + "person where cpf = '12345678901'", Long.class);
            assertNotNull(nextId);
            assertTrue(nextId > personId, "Os IDs gerados devem continuar após os dados migrados.");
        } finally {
            // Somente o schema exclusivo e aleatório criado por este teste é removido.
            jdbc.execute("drop schema if exists \"" + schema + "\" cascade");
        }
    }
}
