package com.example.qualificamaisanalitic.controllers;

import com.controlers.RegisterController;
import com.entities.Course;
import com.enums.StatusCourse;
import com.entities.Person;
import com.entities.Register;
import com.enums.StatusRegister;
import com.repositories.CourseRepository;
import com.repositories.PersonRepository;
import com.repositories.RegisterRepository;
import com.services.RegisterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegisterControllerTests {
    private RegisterRepository registers;
    private PersonRepository people;
    private CourseRepository courses;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        registers = mock(RegisterRepository.class);
        people = mock(PersonRepository.class);
        courses = mock(CourseRepository.class);
        var service = new RegisterService(registers, people, courses);
        mvc = MockMvcBuilders.standaloneSetup(new RegisterController(service)).build();
    }

    @Test
    void listsAllRegistrationsWithPeopleCoursesDatesAndStatuses() throws Exception {
        var day = LocalDate.of(2026, 10, 1);
        var person = new Person();
        person.setCpf("01234567890");
        person.setFullName("Pessoa A");
        var otherPerson = new Person();
        otherPerson.setCpf("98765432100");
        otherPerson.setFullName("Pessoa B");
        var course = new Course("Curso A", null, day, day.plusMonths(1), StatusCourse.ACTIVE);
        course.setId(3L);
        var otherCourse = new Course("Curso B", null, day, day.plusMonths(1), StatusCourse.ACTIVE);
        otherCourse.setId(4L);
        var active = new Register(person, course, day, StatusRegister.ACTIVE);
        active.setId(17L);
        var canceled = new Register(otherPerson, otherCourse, day.plusDays(1), StatusRegister.CANCELED);
        canceled.setId(18L);
        when(registers.findAll()).thenReturn(List.of(active, canceled));

        mvc.perform(get("/api/register"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(17))
                .andExpect(jsonPath("$[0].person.cpf").value("01234567890"))
                .andExpect(jsonPath("$[0].person.fullName").value("Pessoa A"))
                .andExpect(jsonPath("$[0].courseOfInterest.id").value(3))
                .andExpect(jsonPath("$[0].courseOfInterest.name").value("Curso A"))
                .andExpect(jsonPath("$[0].registerDate").value("2026-10-01"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].id").value(18))
                .andExpect(jsonPath("$[1].person.cpf").value("98765432100"))
                .andExpect(jsonPath("$[1].courseOfInterest.id").value(4))
                .andExpect(jsonPath("$[1].registerDate").value("2026-10-02"))
                .andExpect(jsonPath("$[1].status").value("CANCELED"));
        verify(registers).findAll();
        verifyNoMoreInteractions(registers);
        verifyNoInteractions(people, courses);
    }

    @Test
    void returnsEmptyArrayWhenThereAreNoRegistrations() throws Exception {
        when(registers.findAll()).thenReturn(List.of());

        mvc.perform(get("/api/register"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json("[]"));
        verify(registers).findAll();
        verifyNoMoreInteractions(registers);
        verifyNoInteractions(people, courses);
    }
}
