package com.example.qualificamaisanalitic.controllers;

import com.controlers.ApiExceptionHandler;
import com.controlers.CourseController;
import com.services.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.SQLException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTests {
    private CourseService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CourseService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CourseController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void unexpectedFailureDoesNotExposeInternalDetails() throws Exception {
        when(service.getAllCourses()).thenThrow(new IllegalStateException("Internal SQL and private data"));
        mvc.perform(get("/api/course")).andExpect(status().isInternalServerError())
                .andExpect(content().json("""
                        {"status":500,"message":"Não foi possível concluir a operação. Tente novamente mais tarde."}
                        """))
                .andExpect(jsonPath("$.trace").doesNotExist()).andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void lockFailureReturnsRetryableConflict() throws Exception {
        when(service.getAllCourses()).thenThrow(new CannotAcquireLockException("internal lock details"));
        mvc.perform(get("/api/course")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Outra operação alterou ou está utilizando estes dados. Tente novamente."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"23502", "23514", "22001"})
    void databaseInvalidValuesAreBadRequestsRatherThanConflicts(String state) throws Exception {
        when(service.getAllCourses()).thenThrow(new DataIntegrityViolationException("SQL with values",
                new SQLException("private values", state)));
        mvc.perform(get("/api/course")).andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"status":400,"message":"Preencha os campos obrigatórios com valores válidos."}
                        """));
    }

    @Test
    void unknownDatabaseFailureIsNotMisreportedAsClientError() throws Exception {
        when(service.getAllCourses()).thenThrow(new DataIntegrityViolationException("unknown driver failure"));
        mvc.perform(get("/api/course")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }
}
