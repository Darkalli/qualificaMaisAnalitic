package com.example.qualificamaisanalitic.controllers;

import com.controlers.CourseClassController;
import com.entities.Course;
import com.entities.CourseClass;
import com.enums.Period;
import com.enums.StatusClass;
import com.mappers.CourseClassMapper;
import com.repositories.*;
import com.services.CourseClassService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseClassPeriodStatusControllerTests {
    private CourseClassRepository classes;
    private MockMvc mvc;
    private CourseClass existing;

    @BeforeEach
    void setUp() {
        classes = mock(CourseClassRepository.class);
        var courses = mock(CourseRepository.class);
        var course = new Course();
        course.setId(3L);
        when(courses.findByid(3L)).thenReturn(Optional.of(course));
        when(courses.findByIdForUpdate(3L)).thenReturn(Optional.of(course));
        when(classes.save(any(CourseClass.class))).thenAnswer(call -> {
            CourseClass saved = call.getArgument(0);
            saved.setId(4L);
            return saved;
        });
        existing = new CourseClass(LocalDate.of(2026, 10, 28), Period.MORNING,
                LocalTime.of(8, 0), LocalTime.of(10, 0), course, StatusClass.POSTPONED);
        existing.setId(4L);
        when(classes.findCourseIdById(4L)).thenReturn(Optional.of(3L));
        when(classes.findById(4L)).thenReturn(Optional.of(existing));
        when(classes.findByCourse(course)).thenReturn(List.of(existing));
        var service = new CourseClassService(classes, courses, Mappers.getMapper(CourseClassMapper.class),
                mock(PersonRepository.class), mock(RegisterRepository.class), mock(PresenceRepository.class));
        mvc = MockMvcBuilders.standaloneSetup(new CourseClassController(service)).build();
    }

    @ParameterizedTest
    @EnumSource(StatusClass.class)
    void createsSingleAndBatchClassesWithSelectedStatus(StatusClass selected) throws Exception {
        for (boolean batch : new boolean[]{false, true}) {
            mvc.perform(post(batch ? "/api/courseClass/courseClass/batch" : "/api/courseClass")
                            .contentType(APPLICATION_JSON).content(payload(batch, "NIGHT", ",\"statusClass\":\"" + selected.name() + "\"")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath(batch ? "$[0].statusClass" : "$.statusClass").value(selected.name()))
                    .andExpect(jsonPath(batch ? "$[0].session" : "$.session").value("NIGHT"));
        }
    }

    @ParameterizedTest
    @EnumSource(Period.class)
    void createsSingleAndBatchClassesWithSelectedPeriod(Period selected) throws Exception {
        for (boolean batch : new boolean[]{false, true}) {
            mvc.perform(post(batch ? "/api/courseClass/courseClass/batch" : "/api/courseClass")
                            .contentType(APPLICATION_JSON).content(payload(batch, selected.name(), "")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath(batch ? "$[0].session" : "$.session").value(selected.name()))
                    .andExpect(jsonPath(batch ? "$[0].statusClass" : "$.statusClass").value("ACTIVE"));
        }
    }

    @Test
    void nullCreationStatusDefaultsToActiveForSingleAndBatch() throws Exception {
        for (boolean batch : new boolean[]{false, true}) {
            mvc.perform(post(batch ? "/api/courseClass/courseClass/batch" : "/api/courseClass")
                            .contentType(APPLICATION_JSON).content(payload(batch, "MORNING", ",\"statusClass\":null")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath(batch ? "$[0].statusClass" : "$.statusClass").value("ACTIVE"));
        }
    }

    @Test
    void patchesPeriodAndStatusAndPreservesThemWhenOmittedOrNull() throws Exception {
        for (String body : new String[]{
                "{\"classId\":4,\"session\":\"AFTERNOON\",\"statusClass\":\"CANCELED\"}",
                "{\"classId\":4}",
                "{\"classId\":4,\"session\":null,\"statusClass\":null}"}) {
            mvc.perform(patch("/api/courseClass").contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.session").value("AFTERNOON"))
                    .andExpect(jsonPath("$.statusClass").value("CANCELED"))
                    .andExpect(jsonPath("$.day").value("2026-10-28"))
                    .andExpect(jsonPath("$.course.id").value(3));
        }
        mvc.perform(get("/api/courseClass/courseClass/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].session").value("AFTERNOON"))
                .andExpect(jsonPath("$[0].statusClass").value("CANCELED"));
    }

    @Test
    void rejectsUnknownEnumsBeforeSavingForEveryWriteEndpoint() throws Exception {
        for (boolean batch : new boolean[]{false, true}) {
            String path = batch ? "/api/courseClass/courseClass/batch" : "/api/courseClass";
            mvc.perform(post(path).contentType(APPLICATION_JSON).content(payload(batch, "UNKNOWN", "")))
                    .andExpect(status().isBadRequest());
            mvc.perform(post(path).contentType(APPLICATION_JSON).content(payload(batch, "MORNING", ",\"statusClass\":\"UNKNOWN\"")))
                    .andExpect(status().isBadRequest());
        }
        for (String field : new String[]{"session", "statusClass"}) {
            mvc.perform(patch("/api/courseClass").contentType(APPLICATION_JSON)
                            .content("{\"classId\":4,\"" + field + "\":\"UNKNOWN\"}"))
                    .andExpect(status().isBadRequest());
        }
        verify(classes, never()).save(any());
    }

    private String payload(boolean batch, String period, String extra) {
        return "{\"day\":" + (batch ? "[\"2026-10-28\"]" : "\"2026-10-28\"")
                + ",\"session\":\"" + period + "\",\"start\":\"18:00:00\",\"finish\":\"20:00:00\",\"courseId\":3" + extra + "}";
    }
}
