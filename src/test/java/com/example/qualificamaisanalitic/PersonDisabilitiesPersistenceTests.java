package com.example.qualificamaisanalitic;

import com.entities.Person;
import com.enums.Disabilities;
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
class PersonDisabilitiesPersistenceTests {
    @Autowired
    private EntityManager entityManager;

    @Test
    void savesLoadsAndUpdatesMultipleDisabilitiesByEnumName() {
        var person = PersonTestData.person("01234567890");
        person.setDisabilities(EnumSet.of(Disabilities.HEARING, Disabilities.VISUAL));
        entityManager.persist(person);
        entityManager.flush();
        Long id = person.getId();
        entityManager.clear();

        var saved = entityManager.find(Person.class, id);
        assertEquals(Set.of(Disabilities.HEARING, Disabilities.VISUAL), saved.getDisabilities());
        assertEquals(Set.of("HEARING", "VISUAL"), Set.copyOf(entityManager.createNativeQuery(
                "select disability from person_disabilities where person_id = :id", String.class)
                .setParameter("id", id).getResultList()));

        saved.getDisabilities().remove(Disabilities.HEARING);
        saved.getDisabilities().add(Disabilities.MOTOR);
        saved.getDisabilities().add(Disabilities.VISUAL);
        entityManager.flush();
        entityManager.clear();
        assertEquals(Set.of(Disabilities.MOTOR, Disabilities.VISUAL),
                entityManager.find(Person.class, id).getDisabilities());
    }
}
