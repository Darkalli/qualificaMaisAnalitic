package com.example.qualificamaisanalitic.controllers;

import com.controlers.CourseController;
import com.entities.Course;
import com.enums.Period;
import com.enums.StatusCourse;
import com.mappers.CourseMapper;
import com.repositories.CourseRepository;
import com.services.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CoursePeriodControllerTests {
    private CourseRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(CourseRepository.class);
        when(repository.save(any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            course.setId(3L);
            return course;
        });
        var service = new CourseService(repository, Mappers.getMapper(CourseMapper.class),
                mock(ApplicationEventPublisher.class));
        mvc = MockMvcBuilders.standaloneSetup(new CourseController(service)).build();
    }

    @Test
    void updatesOnlyThePeriodWithoutChangingOtherFields() throws Exception {
        var course = new Course("Informática", "Introdução", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1), StatusCourse.ACTIVE);
        course.setId(3L);
        course.setPeriod(Period.MORNING);
        when(repository.findByid(3L)).thenReturn(Optional.of(course));

        mvc.perform(patch("/api/course").contentType(APPLICATION_JSON)
                        .content("{\"courseId\":3, \"period\":\"AFTERNOON\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("AFTERNOON"))
                .andExpect(jsonPath("$.name").value("Informática"))
                .andExpect(jsonPath("$.description").value("Introdução"))
                .andExpect(jsonPath("$.start").value("2026-10-01"))
                .andExpect(jsonPath("$.statusCourse").value("ACTIVE"));
    }

    @Test
    void preservesPeriodWhenItIsOmittedOrNullInPartialUpdate() throws Exception {
        var course = new Course("Informática", null, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1), StatusCourse.ACTIVE);
        course.setPeriod(Period.MORNING);
        when(repository.findByid(3L)).thenReturn(Optional.of(course));
        for (String request : new String[]{"{\"courseId\":3,\"name\":\"Novo\"}",
                "{\"courseId\":3,\"name\":\"Novo\",\"period\":null}"}) {
            mvc.perform(patch("/api/course").contentType(APPLICATION_JSON).content(request))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period").value("MORNING"))
                    .andExpect(jsonPath("$.name").value("Novo"));
        }
    }

    @Test
    void acceptsLegacyCreationWithoutPeriod() throws Exception {
        mvc.perform(post("/api/course").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Curso sem período\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.period").doesNotExist());
    }

    @Test
    void listsThePeriodWithTheCourse() throws Exception {
        var course = new Course();
        course.setPeriod(Period.MORNING);
        when(repository.findAll()).thenReturn(List.of(course));
        mvc.perform(get("/api/course"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].period").value("MORNING"));
    }

    @Test
    void rejectsUnknownPeriodOnCreationAndUpdate() throws Exception {
        mvc.perform(post("/api/course").contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Curso\",\"period\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/course").contentType(APPLICATION_JSON)
                        .content("{\"courseId\":3,\"period\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        verify(repository, never()).save(any(Course.class));
    }

    @ParameterizedTest
    @EnumSource(Period.class)
    void createsCourseWithTheSelectedPeriod(Period period) throws Exception {
        mvc.perform(post("/api/course").contentType(APPLICATION_JSON).content("""
                {"name":"Informática", "description":"Introdução", "start":"2026-10-01",
                 "finish":"2026-11-01", "period":"%s"}
                """.formatted(period.name())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Informática"))
                .andExpect(jsonPath("$.period").value(period.name()))
                .andExpect(jsonPath("$.statusCourse").value("ACTIVE"));
    }
}
