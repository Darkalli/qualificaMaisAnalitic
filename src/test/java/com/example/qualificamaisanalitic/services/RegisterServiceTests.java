package com.example.qualificamaisanalitic.services;

import com.dtos.registerDtos.AddRegisterDto;
import com.dtos.registerDtos.SearchRegisterDto;
import com.entities.Register;
import com.entities.Course;
import com.entities.CourseClass;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalTime;
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
        when(people.findByCpfForUpdate(CPF)).thenReturn(Optional.of(person));
        when(courses.findByIdForRegistration(3L)).thenReturn(Optional.of(course));
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
        when(courses.findByIdForRegistration(3L)).thenReturn(Optional.of(course(3L)));
        when(people.findByCpfForUpdate(CPF)).thenReturn(Optional.empty());
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
        when(courses.findByIdForRegistration(99L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.addRegister(new AddRegisterDto(CPF, 99L, DAY)));
        verifyNoInteractions(people, registers);
    }

    private Course course(long id) {
        var course = new Course("Curso de exemplo", null, DAY, DAY.plusMonths(1));
        course.setId(id);
        return course;
    }

    @ParameterizedTest
    @ValueSource(strings = {"012.345.678-90", "01234567890abc", " 012.345.678-90 "})
    void normalizesCpfInAllFourRegistrationOperations(String input) {
        var person = ServiceTestData.person();
        var course = course(3L);
        var registration = new Register(person, course, DAY);
        when(people.findByCpfForUpdate(CPF)).thenReturn(Optional.of(person));
        when(courses.findByIdForRegistration(3L)).thenReturn(Optional.of(course));
        when(registers.save(any(Register.class))).thenReturn(registration);
        when(registers.findByPerson_Cpf(CPF)).thenReturn(List.of(registration));
        when(registers.findByPerson_CpfAndCourseOfInterest_Id(CPF, 3L))
                .thenReturn(Optional.of(registration));

        assertSame(registration, service.addRegister(new AddRegisterDto(input, 3L, DAY)));
        assertEquals(List.of(registration), service.getAllRegisterByCpf(input));
        var key = new SearchRegisterDto(input, 3L);
        assertSame(registration, service.getByPersonCpfAndCourseOfInterest(key));
        service.deleteRegister(key);

        verify(people).findByCpfForUpdate(CPF);
        verify(registers, times(2)).findByPerson_Cpf(CPF);
        verify(registers, times(2)).findByPerson_CpfAndCourseOfInterest_Id(CPF, 3L);
        verify(registers).delete(registration);
        verify(registers).save(any(Register.class));
        verifyNoMoreInteractions(registers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "0123456789", "012345678901", "012.345.678-9"})
    void rejectsInvalidCpfInAllOperationsBeforeAccessingRepositories(String input) {
        var key = new SearchRegisterDto(input, 3L);
        assertThrows(IllegalArgumentException.class,
                () -> service.addRegister(new AddRegisterDto(input, 3L, DAY)));
        assertThrows(IllegalArgumentException.class, () -> service.getAllRegisterByCpf(input));
        assertThrows(IllegalArgumentException.class, () -> service.getByPersonCpfAndCourseOfInterest(key));
        assertThrows(IllegalArgumentException.class, () -> service.deleteRegister(key));
        verifyNoInteractions(registers, people, courses);
    }

    @ParameterizedTest(name = "existing 08–10, new {0}–{1}, day offset {2}: conflict={3}")
    @CsvSource({
            "7, 9, 0, true", "9, 11, 0, true", "8, 10, 0, true",
            "7, 11, 0, true", "8, 9, 0, true", "9, 10, 0, true",
            "6, 8, 0, false", "10, 12, 0, false",
            "6, 7, 0, false", "11, 12, 0, false",
            "8, 10, 1, false", "8, 10, -1, false"
    })
    void checksScheduleOverlapAndAllowsTouchingIntervalsAndDifferentDays(
            int start, int finish, int dayOffset, boolean conflict) {
        var person = ServiceTestData.person();
        var existing = course(3L);
        var requested = course(4L);
        addClass(existing, DAY, 8, 10);
        addClass(requested, DAY.plusDays(dayOffset), start, finish);
        when(people.findByCpfForUpdate(CPF)).thenReturn(Optional.of(person));
        when(courses.findByIdForRegistration(4L)).thenReturn(Optional.of(requested));
        when(registers.findByPerson_Cpf(CPF)).thenReturn(List.of(new Register(person, existing, DAY)));
        var input = new AddRegisterDto("012.345.678-90", 4L, DAY);

        if (conflict) {
            assertThrows(IllegalArgumentException.class, () -> service.addRegister(input));
            verify(registers, never()).save(any());
        } else {
            service.addRegister(input);
            verify(registers).save(argThat(r -> r.getPerson() == person && r.getCourseOfInterest() == requested));
        }
        verify(people, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"false, false", "true, false", "false, true"})
    void currentlyAllowsRegistrationWhenEitherCourseHasNoClasses(boolean existingHasClass, boolean requestedHasClass) {
        var person = ServiceTestData.person();
        var existing = course(3L);
        var requested = course(4L);
        if (existingHasClass) addClass(existing, DAY, 8, 10);
        if (requestedHasClass) addClass(requested, DAY, 8, 10);
        when(people.findByCpfForUpdate(CPF)).thenReturn(Optional.of(person));
        when(courses.findByIdForRegistration(4L)).thenReturn(Optional.of(requested));
        when(registers.findByPerson_Cpf(CPF)).thenReturn(List.of(new Register(person, existing, DAY)));

        service.addRegister(new AddRegisterDto(CPF, 4L, DAY));

        verify(registers).save(any(Register.class));
    }

    @Test
    void checksLaterRegistrationsAndLaterClassesBeforeSaving() {
        var person = ServiceTestData.person();
        var unrelated = course(2L);
        var existing = course(3L);
        var requested = course(4L);
        addClass(unrelated, DAY.minusDays(1), 8, 10);
        addClass(existing, DAY, 8, 10);
        addClass(existing, DAY.plusDays(1), 14, 16);
        addClass(requested, DAY, 10, 12);
        addClass(requested, DAY.plusDays(1), 15, 17);
        when(people.findByCpfForUpdate(CPF)).thenReturn(Optional.of(person));
        when(courses.findByIdForRegistration(4L)).thenReturn(Optional.of(requested));
        when(registers.findByPerson_Cpf(CPF)).thenReturn(List.of(
                new Register(person, unrelated, DAY), new Register(person, existing, DAY)));

        assertThrows(IllegalArgumentException.class, () -> service.addRegister(new AddRegisterDto(CPF, 4L, DAY)));

        verify(registers, never()).save(any());
    }

    private void addClass(Course course, LocalDate day, int start, int finish) {
        course.getCourseClass().add(new CourseClass(day, "Sessão", LocalTime.of(start, 0), LocalTime.of(finish, 0), course));
    }
}
