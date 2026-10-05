package com.example.qualificamaisanalitic.utils;

import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Register;
import com.enums.StatusClass;
import com.enums.StatusRegister;
import com.example.qualificamaisanalitic.PersonTestData;
import com.repositories.RegisterRepository;
import com.utils.RegisterUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUtilsTests {
    private static final String CPF = "01234567890";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    @Mock private RegisterRepository registers;

    @ParameterizedTest
    @EnumSource(StatusRegister.class)
    void registrationConflictOnlyCountsActiveRegistrations(StatusRegister status) {
        var existing = registration(3L, 17L, 8, 10, status);
        var requested = registration(4L, null, 9, 11, StatusRegister.ACTIVE);
        when(registers.findByPerson_Cpf(CPF)).thenReturn(List.of(existing));

        assertEquals(status == StatusRegister.ACTIVE,
                RegisterUtils.hasScheduleConflict(registers, requested));
    }

    @ParameterizedTest
    @EnumSource(StatusRegister.class)
    void changedClassConflictOnlyCountsActiveRegistrations(StatusRegister status) {
        var existing = registration(3L, 17L, 8, 10, status);
        var changed = registration(4L, null, 9, 11, StatusRegister.ACTIVE)
                .getCourseOfInterest().getCourseClass().getFirst();
        when(registers.findByPerson_Cpf(CPF)).thenReturn(List.of(existing));

        assertEquals(status == StatusRegister.ACTIVE,
                RegisterUtils.hasScheduleConflict(registers, CPF, changed));
    }

    @Test
    void registrationDoesNotConflictWithItsOwnPersistedIdentity() {
        var requested = registration(3L, 17L, 8, 10, StatusRegister.ACTIVE);
        when(registers.findByPerson_Cpf(CPF)).thenReturn(List.of(requested));

        assertFalse(RegisterUtils.hasScheduleConflict(registers, requested));
    }

    @Test
    void changedClassDoesNotConflictWithItsOwnPersistedIdentity() {
        var existing = registration(3L, 17L, 8, 10, StatusRegister.ACTIVE);
        var changed = existing.getCourseOfInterest().getCourseClass().getFirst();
        changed.setId(21L);
        when(registers.findByPerson_Cpf(CPF)).thenReturn(List.of(existing));

        assertFalse(RegisterUtils.hasScheduleConflict(registers, CPF, changed));
    }

    @ParameterizedTest
    @EnumSource(value = StatusClass.class, names = {"CANCELED", "POSTPONED"})
    void inactiveClassesDoNotConflictInEitherOverload(StatusClass status) {
        var existing = registration(3L, 17L, 8, 10, StatusRegister.ACTIVE);
        existing.getCourseOfInterest().getCourseClass().getFirst().setStatusClass(status);
        var requested = registration(4L, null, 9, 11, StatusRegister.ACTIVE);
        when(registers.findByPerson_Cpf(CPF)).thenReturn(List.of(existing));

        assertFalse(RegisterUtils.hasScheduleConflict(registers, requested));
        assertFalse(RegisterUtils.hasScheduleConflict(registers, CPF,
                requested.getCourseOfInterest().getCourseClass().getFirst()));
    }

    private Register registration(Long courseId, Long registerId, int start, int finish, StatusRegister status) {
        var course = new Course("Curso de exemplo", null, DAY, DAY.plusDays(1));
        course.setId(courseId);
        course.getCourseClass().add(new CourseClass(DAY, "Manhã", LocalTime.of(start, 0),
                LocalTime.of(finish, 0), course));
        var registration = new Register(PersonTestData.person(CPF), course, DAY, status);
        registration.setId(registerId);
        return registration;
    }
}
