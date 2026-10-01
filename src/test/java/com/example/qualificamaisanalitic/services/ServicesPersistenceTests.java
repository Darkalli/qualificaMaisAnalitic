package com.example.qualificamaisanalitic.services;

import com.dtos.CourseDtos.AddCourseDto;
import com.dtos.CourseDtos.UpdateCourseDto;
import com.dtos.courseClassesDtos.AddCourseClassDto;
import com.dtos.courseClassesDtos.UpdateCourseClassDto;
import com.dtos.personDtos.UpdatePersonDto;
import com.dtos.presenceDtos.AddPresenceDto;
import com.dtos.presenceDtos.PresenceByDayAndCourseDto;
import com.dtos.presenceDtos.PresenceUpdateDto;
import com.dtos.registerDtos.AddRegisterDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Person;
import com.enums.Disabilities;
import com.enums.PresenceStatus;
import com.example.qualificamaisanalitic.PersonTestData;
import com.services.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import com.repositories.PersonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ServicesPersistenceTests {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    @Autowired private CourseService courses;
    @Autowired private CourseClassService classes;
    @Autowired private PersonService people;
    @Autowired private RegisterService registers;
    @Autowired private PresenceService presences;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactions;
    @Autowired private PersonRepository personRepository;

    @Test
    void createsUpdatesQueriesAndDeletesCourseAndClassThroughServices() {
        courses.addCourse(new AddCourseDto("Curso de teste", "Descrição", DAY, DAY.plusMonths(1)));
        var course = courses.getCourseByName("Curso de teste");
        classes.addCourseClass(new AddCourseClassDto(DAY, "Manhã", DAY.atTime(8, 0), DAY.atTime(10, 0), course));
        entityManager.flush();
        entityManager.clear();
        var courseClass = classes.allClassesByCourseId(course.getId()).getFirst();
        courses.updateCourse(new UpdateCourseDto(course.getId(), "Curso atualizado", null, null, null));
        classes.updateCourseClass(new UpdateCourseClassDto(courseClass.getId(), null, "Tarde",
                DAY.atTime(14, 0), DAY.atTime(16, 0), null));
        entityManager.flush();
        entityManager.clear();
        var savedCourse = courses.getCourseByName("Curso atualizado");
        assertEquals(course.getId(), savedCourse.getId());
        assertEquals("Descrição", savedCourse.getDescription());
        assertEquals(DAY, savedCourse.getStart());
        var savedClass = savedCourse.getCourseClass().getFirst();
        assertEquals(courseClass.getId(), savedClass.getId());
        assertEquals("Tarde", savedClass.getSession());
        assertEquals(DAY.atTime(14, 0), savedClass.getStart());
        assertEquals(DAY.atTime(16, 0), savedClass.getFinish());
        classes.deleteCourseClass(savedClass.getId());
        entityManager.flush();
        entityManager.clear();
        assertNull(entityManager.find(CourseClass.class, courseClass.getId()));
        courses.deleteCourse(course.getId());
        entityManager.flush();
        entityManager.clear();
        assertThrows(EntityNotFoundException.class, () -> courses.getCourseByName("Curso atualizado"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void updatesPersonDisabilitiesWithoutCallerTransactionAndRollsBackInvalidUpdate() {
        // Não envolver o serviço na transação do teste: isso esconderia acesso lazy fora de transação.
        String cpf = "87654321009";
        try {
            people.addPerson(ServiceTestData.addPerson(cpf, "+55 (11) 99999-0000", "(11) 3333-4444"));
            Long personId = people.getByCpf(cpf).getId();
            people.updatePerson(new UpdatePersonDto("876.543.210-09", "Nome atualizado", "novo@example.com",
                    "+55 (21) 98888-7777", "(21) 2222-3333", null, null, null, null,
                    Set.of("Física/Motora")));
            transactions.executeWithoutResult(status -> {
                var saved = entityManager.find(Person.class, personId);
                assertEquals("Nome atualizado", saved.getSocialName());
                assertEquals("novo@example.com", saved.getEmail());
                assertEquals("21988887777", saved.getPersonalPhone());
                assertEquals("2122223333", saved.getFamilyPhone());
                assertEquals(Set.of(Disabilities.MOTOR), saved.getDisabilities());
            });
            assertThrows(IllegalArgumentException.class, () -> people.updatePerson(new UpdatePersonDto(cpf,
                    "Não deve salvar", "nao-salvar@example.com", "123", null, null, null, null, null, null)));
            transactions.executeWithoutResult(status -> {
                var saved = entityManager.find(Person.class, personId);
                assertEquals("Nome atualizado", saved.getSocialName());
                assertEquals("novo@example.com", saved.getEmail());
                assertEquals("21988887777", saved.getPersonalPhone());
            });
        } finally {
            // Remove somente o cadastro fictício deste teste, que não usa rollback automático.
            transactions.executeWithoutResult(status -> personRepository.findByCpf(cpf).ifPresent(person -> {
                var address = person.getAddress();
                entityManager.remove(person);
                entityManager.flush();
                entityManager.remove(address);
            }));
        }
    }

    @Test
    void directRegistrationsReusePersonAndDatabaseRejectsDuplicateCourse() {
        String cpf = "76543210900";
        people.addPerson(ServiceTestData.addPerson(cpf, "11999990000", null));
        var personId = people.getByCpf(cpf).getId();
        var firstCourse = new Course("Informática", null, DAY, DAY.plusMonths(1));
        var secondCourse = new Course("Inglês", null, DAY, DAY.plusMonths(1));
        entityManager.persist(firstCourse);
        entityManager.persist(secondCourse);
        registers.addRegister(new AddRegisterDto(cpf, firstCourse.getId(), DAY));
        registers.addRegister(new AddRegisterDto(cpf, secondCourse.getId(), DAY));
        entityManager.flush();
        entityManager.clear();
        var saved = registers.getAllRegisterByCpf(cpf);
        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(register -> register.getPerson().getId().equals(personId)));
        assertEquals(1L, entityManager.createQuery("select count(p) from Person p where p.cpf = :cpf", Long.class)
                .setParameter("cpf", cpf).getSingleResult());
        assertThrows(DataIntegrityViolationException.class,
                () -> registers.addRegister(new AddRegisterDto(cpf, firstCourse.getId(), DAY.plusDays(1))));
    }

    @Test
    void attendanceServicesPersistAndUpdateOnlySelectedCourse() {
        var person = PersonTestData.person("65432109800");
        var firstCourse = new Course("Curso A", null, DAY, DAY.plusMonths(1));
        var secondCourse = new Course("Curso B", null, DAY, DAY.plusMonths(1));
        entityManager.persist(person);
        entityManager.persist(firstCourse);
        entityManager.persist(secondCourse);
        var firstClass = new CourseClass(DAY, "Manhã", DAY.atTime(8, 0), DAY.atTime(10, 0), firstCourse);
        var secondClass = new CourseClass(DAY, "Tarde", DAY.atTime(14, 0), DAY.atTime(16, 0), secondCourse);
        entityManager.persist(firstClass);
        entityManager.persist(secondClass);
        presences.addPresence(new AddPresenceDto(person.getId(), firstClass.getId(), PresenceStatus.PRESENT));
        presences.addPresence(new AddPresenceDto(person.getId(), secondClass.getId(), PresenceStatus.ABSENT));
        entityManager.flush();
        entityManager.clear();
        presences.updatePresence(new PresenceUpdateDto(person.getId(), secondClass.getId(), PresenceStatus.JUSTIFIED));
        entityManager.flush();
        entityManager.clear();
        assertEquals(2, presences.getPresenceByPerson(person.getId()).size());
        assertEquals(PresenceStatus.PRESENT, presences.getPresenceByDateAndCourse(
                new PresenceByDayAndCourseDto(firstCourse.getId(), firstClass.getId())).getFirst().getStatus());
        assertEquals(PresenceStatus.JUSTIFIED, presences.getPresenceByDateAndCourse(
                new PresenceByDayAndCourseDto(secondCourse.getId(), secondClass.getId())).getFirst().getStatus());
        assertTrue(presences.getPresenceByDateAndCourse(
                new PresenceByDayAndCourseDto(firstCourse.getId(), secondClass.getId())).isEmpty());
    }
}
