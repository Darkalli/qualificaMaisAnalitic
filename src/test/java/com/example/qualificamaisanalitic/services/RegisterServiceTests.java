package com.example.qualificamaisanalitic.services;

import com.dtos.registerDtos.AddRegisterDto;
import com.dtos.registerDtos.SearchRegisterDto;
import com.entities.Register;
import com.entities.Course;
import com.repositories.CourseRepository;
import com.repositories.PersonRepository;
import com.repositories.RegisterRepository;
import com.services.RegisterService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterServiceTests {
    private static final String CPF = "01234567890";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    @Mock private RegisterRepository registers;
    @Mock private PersonRepository people;
    @Mock private CourseRepository courses;
    private RegisterService service;

    @BeforeEach
    void setUp() {
        service = new RegisterService(registers, people, courses);
    }

    @Test
    void newRegistrationReusesPersonWithoutResavingPersonalData() {
        var person = ServiceTestData.person();
        var course = course(3L);
        when(people.findByCpf(CPF)).thenReturn(Optional.of(person));
        when(courses.findById(3L)).thenReturn(Optional.of(course));
        service.addRegister(new AddRegisterDto(CPF, 3L, DAY));
        var capture = ArgumentCaptor.forClass(Register.class);
        verify(registers).save(capture.capture());
        var saved = capture.getValue();
        assertNull(saved.getId());
        assertSame(person, saved.getPerson());
        assertSame(course, saved.getCourseOfInterest());
        assertEquals(DAY, saved.getRegisterDate());
        verify(people, never()).save(any());
        verify(courses, never()).save(any());
    }

    @Test
    void refusesRegistrationOfUnknownPerson() {
        when(courses.findById(3L)).thenReturn(Optional.of(course(3L)));
        when(people.findByCpf(CPF)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.addRegister(new AddRegisterDto(CPF, 3L, DAY)));
        verifyNoInteractions(registers);
    }

    @Test
    void searchesAndDeletesRegistrationUsingBothCpfAndCourse() {
        var registration = new Register(ServiceTestData.person(), course(3L), DAY);
        var key = new SearchRegisterDto(CPF, 3L);
        when(registers.findByPerson_CpfAndCourseOfInterest_Id(CPF, 3L))
                .thenReturn(Optional.of(registration));
        assertSame(registration, service.getByPersonCpfAndCourseOfInterest(key));
        service.deleteRegister(key);
        verify(registers).delete(registration);
        verifyNoInteractions(people);
    }

    @Test
    void reportsMissingRegistrationForSearchAndDeletion() {
        var key = new SearchRegisterDto(CPF, 99L);
        when(registers.findByPerson_CpfAndCourseOfInterest_Id(CPF, 99L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.getByPersonCpfAndCourseOfInterest(key));
        assertThrows(EntityNotFoundException.class, () -> service.deleteRegister(key));
        verify(registers, never()).delete(any());
    }

    @Test
    void listsDifferentRegistrationsOfTheSamePerson() {
        var person = ServiceTestData.person();
        var results = List.of(new Register(person, course(3L), DAY), new Register(person, course(4L), DAY));
        when(registers.findByPerson_Cpf(CPF)).thenReturn(results);
        assertEquals(results, service.getAllRegisterByCpf(CPF));
    }

    @Test
    void refusesUnknownCourseBeforeSavingRegistration() {
        when(courses.findById(99L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.addRegister(new AddRegisterDto(CPF, 99L, DAY)));
        verifyNoInteractions(people, registers);
    }

    private Course course(long id) {
        var course = new Course("Curso de exemplo", null, DAY, DAY.plusMonths(1));
        course.setId(id);
        return course;
    }
}
