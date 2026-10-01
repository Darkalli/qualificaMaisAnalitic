package com.example.qualificamaisanalitic;

import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Presence;
import com.entities.Register;
import com.enums.PresenceStatus;
import com.repositories.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Duas conexões/transações independentes disputam a mesma chave nas restrições existentes. */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentPersistenceTests {
    @Autowired private PersonRepository people;
    @Autowired private CourseRepository courses;
    @Autowired private CourseClassRepository classes;
    @Autowired private RegisterRepository registers;
    @Autowired private PresenceRepository presences;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"register", "presence", "course_class"})
    void databaseAllowsOnlyOneConcurrentInsertForTheSamePersonAndTarget(String table) throws Exception {
        var day = LocalDate.of(2026, 10, 1);
        var person = PersonTestData.person("10987654321");
        var course = new Course("Curso de concorrência", null, day, day.plusDays(1));
        var courseClass = new CourseClass(day, "Manhã", day.atTime(8, 0), day.atTime(10, 0), course);
        transactions.executeWithoutResult(status -> {
            people.saveAndFlush(person);
            courses.saveAndFlush(course);
            if (!table.equals("course_class")) classes.saveAndFlush(courseClass);
        });
        var readyToWrite = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> insert = () -> {
                try {
                    transactions.executeWithoutResult(status -> {
                        // As duas transações confirmam ausência antes de tentar gravar.
                        String filter = table.equals("course_class") ? "course_id" : "person_id";
                        Long targetId = table.equals("course_class") ? course.getId() : person.getId();
                        assertEquals(0, jdbc.queryForObject("select count(*) from " + table
                                + " where " + filter + " = ?", Integer.class, targetId));
                        awaitBothWriters(readyToWrite);
                        if (table.equals("register")) {
                            registers.saveAndFlush(new Register(person, course, day));
                        } else if (table.equals("presence")) {
                            presences.saveAndFlush(new Presence(person, courseClass, course, PresenceStatus.PRESENT));
                        } else {
                            classes.saveAndFlush(new CourseClass(day, "Manhã", day.atTime(8, 0), day.atTime(10, 0), course));
                        }
                    });
                    return true;
                } catch (DataIntegrityViolationException expected) {
                    return false;
                }
            };
            var first = executor.submit(insert);
            var second = executor.submit(insert);
            boolean firstSaved = first.get(20, TimeUnit.SECONDS);
            boolean secondSaved = second.get(20, TimeUnit.SECONDS);
            assertNotEquals(firstSaved, secondSaved, "Exatamente uma transação deve confirmar a gravação");
            String filter = table.equals("course_class") ? "course_id" : "person_id";
            Long targetId = table.equals("course_class") ? course.getId() : person.getId();
            assertEquals(1, jdbc.queryForObject("select count(*) from " + table
                    + " where " + filter + " = ?", Integer.class, targetId));
        } finally {
            transactions.executeWithoutResult(status -> {
                jdbc.update("delete from presence where person_id = ?", person.getId());
                jdbc.update("delete from register where person_id = ?", person.getId());
                jdbc.update("delete from course_class where course_id = ?", course.getId());
                jdbc.update("delete from course where id = ?", course.getId());
                jdbc.update("delete from person_disabilities where person_id = ?", person.getId());
                jdbc.update("delete from person where id = ?", person.getId());
                jdbc.update("delete from address where id = ?", person.getAddress().getId());
            });
        }
    }

    private void awaitBothWriters(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Teste de concorrência interrompido", exception);
        } catch (Exception exception) {
            throw new AssertionError("As duas transações não chegaram à gravação", exception);
        }
    }
}
