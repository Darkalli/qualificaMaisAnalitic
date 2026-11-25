package com.example.qualificamaisanalitic.services;

import com.dtos.registerDtos.AddRegisterDto;
import com.dtos.registerDtos.SearchRegisterDto;
import com.entities.Course;
import com.entities.Person;
import com.entities.Register;
import com.enums.StatusCourse;
import com.enums.StatusRegister;
import com.repositories.CourseRepository;
import com.repositories.PersonRepository;
import com.repositories.RegisterRepository;
import com.services.RegisterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterWaitlistServiceTests {
    private static final String CPF = "01234567890";
    private static final long COURSE_ID = 3L;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 25);
    private static final LocalDateTime REGISTERED_AT = DAY.atTime(8, 30);

    @Mock private RegisterRepository registers;
    @Mock private PersonRepository people;
    @Mock private CourseRepository courses;

    private RegisterService service;
    private Course course;
    private Person person;

    @BeforeEach
    void setUp() {
        service = new RegisterService(registers, people, courses);
        course = new Course("Course with two places", null, DAY, DAY.plusMonths(1),
                StatusCourse.ACTIVE, 2);
        course.setId(COURSE_ID);
        person = person(7L, CPF);
    }

    @ParameterizedTest
    @CsvSource({"0, ACTIVE", "1, ACTIVE", "2, WAITLISTED", "3, WAITLISTED"})
    void assignsNewRegistrationAccordingToAvailablePlaces(int activeCount, StatusRegister expected) {
        stubNewRegistration(activeCount);
        when(registers.save(any(Register.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Register saved = service.addRegister(new AddRegisterDto("012.345.678-90", COURSE_ID));

        assertEquals(expected, saved.getStatus());
        assertSame(person, saved.getPerson());
        assertSame(course, saved.getCourseOfInterest());
        assertNotNull(saved.getRegisterDate());
        verify(registers).save(saved);
        var ordered = inOrder(courses, people, registers);
        ordered.verify(courses).findByIdForUpdate(COURSE_ID);
        ordered.verify(people).findByCpfForUpdate(CPF);
        ordered.verify(registers).countAllActiveByCourseId(COURSE_ID);
        verify(courses, never()).save(any());
        verify(people, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({
            "CANCELED, 0, ACTIVE", "CANCELED, 2, WAITLISTED",
            "WAITLISTED, 0, ACTIVE", "WAITLISTED, 2, WAITLISTED"
    })
    void reactivationRespectsCapacityWithoutReplacingRegistration(
            StatusRegister previousStatus, int activeCount, StatusRegister expected) {
        Register registration = registration(17L, person, previousStatus);
        stubExistingRegistration(registration);
        when(registers.findByPerson_Cpf(CPF)).thenReturn(List.of(registration));
        when(registers.countAllActiveByCourseId(COURSE_ID)).thenReturn(activeCount);

        service.reactiveRegister(new SearchRegisterDto(CPF, COURSE_ID));

        assertEquals(expected, registration.getStatus());
        assertEquals(17L, registration.getId());
        assertEquals(REGISTERED_AT, registration.getRegisterDate());
        assertSame(person, registration.getPerson());
        assertSame(course, registration.getCourseOfInterest());
        verify(registers).save(registration);
        verify(registers, never()).delete(any());
    }

    @Test
    void reactivatingActiveRegistrationDoesNotAllocateAnotherPlace() {
        Register registration = registration(17L, person, StatusRegister.ACTIVE);
        stubExistingRegistration(registration);

        service.reactiveRegister(new SearchRegisterDto(CPF, COURSE_ID));

        assertEquals(StatusRegister.ACTIVE, registration.getStatus());
        verify(registers, never()).countAllActiveByCourseId(anyLong());
        verify(registers, never()).save(any());
    }

    @Test
    void cancelingActiveRegistrationPromotesFirstReserveAfterReleasingItsPlace() {
        Register active = registration(17L, person, StatusRegister.ACTIVE);
        Register reserve = registration(18L, person(8L, "98765432100"), StatusRegister.WAITLISTED);
        stubExistingRegistration(active);
        when(registers.findFirstByCourseOfInterestIdAndStatusOrderByRegisterDate(
                COURSE_ID, StatusRegister.WAITLISTED)).thenReturn(Optional.of(reserve));
        // Emulates the database count after the managed cancellation has been flushed.
        when(registers.countAllActiveByCourseId(COURSE_ID)).thenAnswer(invocation ->
                active.getStatus() == StatusRegister.ACTIVE ? 2 : 1);

        service.deleteRegister(new SearchRegisterDto(CPF, COURSE_ID));

        assertEquals(StatusRegister.CANCELED, active.getStatus());
        assertEquals(StatusRegister.ACTIVE, reserve.getStatus());
        assertEquals(17L, active.getId());
        assertEquals(18L, reserve.getId());
        assertEquals(REGISTERED_AT, active.getRegisterDate());
        assertEquals(REGISTERED_AT, reserve.getRegisterDate());
        verify(registers, never()).delete(any());
        var ordered = inOrder(courses, people, registers);
        ordered.verify(courses).findByIdForUpdate(COURSE_ID);
        ordered.verify(people).findByCpfForUpdate(CPF);
        ordered.verify(registers).findByPerson_CpfAndCourseOfInterest_Id(CPF, COURSE_ID);
    }

    @Test
    void cancelingActiveRegistrationWithEmptyWaitlistStillSucceeds() {
        Register active = registration(17L, person, StatusRegister.ACTIVE);
        stubExistingRegistration(active);
        when(registers.findFirstByCourseOfInterestIdAndStatusOrderByRegisterDate(
                COURSE_ID, StatusRegister.WAITLISTED)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.deleteRegister(new SearchRegisterDto(CPF, COURSE_ID)));

        assertEquals(StatusRegister.CANCELED, active.getStatus());
        verify(registers, never()).delete(any());
    }

    @Test
    void cancelingReserveDoesNotPromoteAnotherReserveEvenWhenCourseHasSpace() {
        Register canceledReserve = registration(17L, person, StatusRegister.WAITLISTED);
        Register nextReserve = registration(18L, person(8L, "98765432100"), StatusRegister.WAITLISTED);
        stubExistingRegistration(canceledReserve);
        // These answers expose an accidental promotion without requiring a queue lookup.
        lenient().when(registers.findFirstByCourseOfInterestIdAndStatusOrderByRegisterDate(
                COURSE_ID, StatusRegister.WAITLISTED)).thenReturn(Optional.of(nextReserve));
        lenient().when(registers.countAllActiveByCourseId(COURSE_ID)).thenReturn(0);

        service.deleteRegister(new SearchRegisterDto(CPF, COURSE_ID));

        assertEquals(StatusRegister.CANCELED, canceledReserve.getStatus());
        assertEquals(StatusRegister.WAITLISTED, nextReserve.getStatus(),
                "Canceling a reserve does not release an occupied place");
        verify(registers, never()).findFirstByCourseOfInterestIdAndStatusOrderByRegisterDate(
                anyLong(), any(StatusRegister.class));
    }

    @Test
    void cancelingAlreadyCanceledRegistrationDoesNotPromoteAnotherReserve() {
        Register canceled = registration(17L, person, StatusRegister.CANCELED);
        stubExistingRegistration(canceled);

        service.deleteRegister(new SearchRegisterDto(CPF, COURSE_ID));

        assertEquals(StatusRegister.CANCELED, canceled.getStatus());
        verify(registers, never()).findFirstByCourseOfInterestIdAndStatusOrderByRegisterDate(
                anyLong(), any(StatusRegister.class));
        verify(registers, never()).countAllActiveByCourseId(anyLong());
        verify(registers, never()).save(any());
    }

    private void stubNewRegistration(int activeCount) {
        when(courses.findByIdForUpdate(COURSE_ID)).thenReturn(Optional.of(course));
        when(people.findByCpfForUpdate(CPF)).thenReturn(Optional.of(person));
        when(registers.findByPerson_Cpf(CPF)).thenReturn(List.of());
        when(registers.countAllActiveByCourseId(COURSE_ID)).thenReturn(activeCount);
    }

    private void stubExistingRegistration(Register registration) {
        when(courses.findByIdForUpdate(COURSE_ID)).thenReturn(Optional.of(course));
        when(people.findByCpfForUpdate(CPF)).thenReturn(Optional.of(person));
        when(registers.findByPerson_CpfAndCourseOfInterest_Id(CPF, COURSE_ID))
                .thenReturn(Optional.of(registration));
        // The course is already attached to the registration; this extra read is optional.
        lenient().when(courses.findByid(COURSE_ID)).thenReturn(Optional.of(course));
    }

    private Register registration(long id, Person owner, StatusRegister status) {
        Register registration = new Register(owner, course, REGISTERED_AT, status);
        registration.setId(id);
        return registration;
    }

    private Person person(long id, String cpf) {
        Person result = new Person();
        result.setId(id);
        result.setCpf(cpf);
        return result;
    }
}
