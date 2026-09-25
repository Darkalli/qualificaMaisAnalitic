package com.example.qualificamaisanalitic;

import com.sheets.entities.Register;
import com.sheets.enums.Disabilities;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RegisterDisabilitiesPersistenceTests {
    @Autowired
    private EntityManager entityManager;

    @Test
    void savesLoadsAndUpdatesMultipleDisabilitiesByEnumName() {
        var register = RegisterTestData.register("01234567890");
        register.setDisabilities(EnumSet.of(Disabilities.HEARING, Disabilities.VISUAL));
        entityManager.persist(register);
        entityManager.flush();
        Long id = register.getId();
        entityManager.clear();

        var saved = entityManager.find(Register.class, id);
        assertEquals(Set.of(Disabilities.HEARING, Disabilities.VISUAL), saved.getDisabilities());
        assertEquals(Set.of("HEARING", "VISUAL"), Set.copyOf(entityManager.createNativeQuery(
                "select disability from register_disabilities where register_id = :id", String.class)
                .setParameter("id", id).getResultList()));

        saved.getDisabilities().remove(Disabilities.HEARING);
        saved.getDisabilities().add(Disabilities.MOTOR);
        saved.getDisabilities().add(Disabilities.VISUAL);
        entityManager.flush();
        entityManager.clear();
        assertEquals(Set.of(Disabilities.MOTOR, Disabilities.VISUAL),
                entityManager.find(Register.class, id).getDisabilities());
    }
}
