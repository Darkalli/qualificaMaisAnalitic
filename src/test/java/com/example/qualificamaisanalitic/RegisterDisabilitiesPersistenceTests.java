package com.example.qualificamaisanalitic;

import com.entities.Register;
import com.enums.Disabilities;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "app.sheets.check-enabled=false",
        "spring.datasource.url=jdbc:h2:mem:register-disabilities;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class RegisterDisabilitiesPersistenceTests {
    @Autowired
    private EntityManager entityManager;

    @Test
    void savesLoadsAndUpdatesMultipleDisabilitiesByEnumName() {
        var register = new Register();
        register.setId(1L);
        register.setDisabilities(EnumSet.of(Disabilities.HEARING, Disabilities.VISUAL));
        entityManager.persist(register);
        entityManager.flush();
        entityManager.clear();

        var saved = entityManager.find(Register.class, 1L);
        assertEquals(Set.of(Disabilities.HEARING, Disabilities.VISUAL), saved.getDisabilities());
        assertEquals(Set.of("HEARING", "VISUAL"), Set.copyOf(entityManager.createNativeQuery(
                "select disability from register_disabilities where register_id = 1", String.class).getResultList()));

        saved.getDisabilities().remove(Disabilities.HEARING);
        saved.getDisabilities().add(Disabilities.MOTOR);
        saved.getDisabilities().add(Disabilities.VISUAL);
        entityManager.flush();
        entityManager.clear();
        assertEquals(Set.of(Disabilities.MOTOR, Disabilities.VISUAL),
                entityManager.find(Register.class, 1L).getDisabilities());
    }
}
