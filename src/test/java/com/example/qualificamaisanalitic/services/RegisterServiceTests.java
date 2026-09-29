package com.example.qualificamaisanalitic.services;

import com.dtos.registerDtos.AddRegisterDto;
import com.dtos.registerDtos.SearchRegisterDto;
import com.entities.Register;
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
    private RegisterService service;

    @BeforeEach
    void setUp() {
        service = new RegisterService(registers, people);
    }

    @Test
    void newRegistrationReusesPersonWithoutResavingPersonalData() {
        var person = ServiceTestData.person();
        when(people.findByCpf(CPF)).thenReturn(Optional.of(person));
        service.addRegister(new AddRegisterDto(CPF, "Informática", DAY));
        var capture = ArgumentCaptor.forClass(Register.class);
        verify(registers).save(capture.capture());
        var saved = capture.getValue();
        assertNull(saved.getId());
        assertSame(person, saved.getPerson());
        assertEquals("Informática", saved.getCourseOfInterest());
        assertEquals(DAY, saved.getRegisterDate());
        verify(people, never()).save(any());
    }

    @Test
    void refusesRegistrationOfUnknownPerson() {
        when(people.findByCpf(CPF)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.addRegister(new AddRegisterDto(CPF, "Informática", DAY)));
        verifyNoInteractions(registers);
    }

    @Test
    void searchesAndDeletesRegistrationUsingBothCpfAndCourse() {
        var registration = new Register(ServiceTestData.person(), "Informática", DAY);
        var key = new SearchRegisterDto(CPF, "Informática");
        when(registers.findByPerson_CpfAndCourseOfInterest(CPF, "Informática"))
                .thenReturn(Optional.of(registration));
        assertSame(registration, service.getByPersonCpfAndCourseOfInterest(key));
        service.deleteRegister(key);
        verify(registers).delete(registration);
        verifyNoInteractions(people);
    }

    @Test
    void reportsMissingRegistrationForSearchAndDeletion() {
        var key = new SearchRegisterDto(CPF, "Ausente");
        when(registers.findByPerson_CpfAndCourseOfInterest(CPF, "Ausente")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.getByPersonCpfAndCourseOfInterest(key));
        assertThrows(EntityNotFoundException.class, () -> service.deleteRegister(key));
        verify(registers, never()).delete(any());
    }

    @Test
    void listsDifferentRegistrationsOfTheSamePerson() {
        var person = ServiceTestData.person();
        var results = List.of(new Register(person, "Informática", DAY), new Register(person, "Inglês", DAY));
        when(registers.findByPerson_Cpf(CPF)).thenReturn(results);
        assertEquals(results, service.getAllRegisterByCpf(CPF));
    }
}
