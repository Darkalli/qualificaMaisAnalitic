package com.example.qualificamaisanalitic.controllers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/** No outer test transaction: queries observe committed data and actual service rollback. */
@SpringBootTest
@ActiveProfiles("test")
class CourseClassBatchTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    long courseId;

    @BeforeEach
    void setUp() throws Exception {
        clean();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(user("batch-tests").roles("AGENT"))).build();
        courseId = createCourse("Curso lote");
    }

    @AfterEach
    void clean() {
        for (String table : new String[]{"presence", "course_class", "register", "person_disabilities", "person", "address", "course"}) {
            jdbc.update("delete from " + table);
        }
    }

    long createCourse(String name) throws Exception {
        var response = mvc.perform(post("/api/course").contentType(APPLICATION_JSON).content("""
                {"name":"%s","description":"Introdução","start":"2026-10-01","finish":"2026-12-31"}
                """.formatted(name))).andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(response.getResponse().getContentAsString(), "$.id")).longValue();
    }

    ResultActions batch(String days, long id, String start, String finish) throws Exception {
        return mvc.perform(post("/api/courseClass/courseClass/batch").contentType(APPLICATION_JSON)
                .content("""
                {"day":%s,"session":"Manhã","start":"%s","finish":"%s","courseId":%d}
                """.formatted(days, start, finish, id)));
    }

    int count() {
        return jdbc.queryForObject("select count(*) from course_class", Integer.class);
    }

    @Test
    void createsAllDatesWithPersistedIdsAndActiveStatus() throws Exception {
        batch("[\"2026-10-03\",\"2026-10-01\"]", courseId, "08:00", "10:00")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].day").value("2026-10-03"))
                .andExpect(jsonPath("$[1].day").value("2026-10-01"))
                .andExpect(jsonPath("$[0].statusClass").value("ACTIVE"))
                .andExpect(jsonPath("$[1].statusClass").value("ACTIVE"))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[1].id").isNumber())
                .andExpect(jsonPath("$[0].course.id").value(courseId));
        assertEquals(2, count());
        assertEquals(2, jdbc.queryForObject("select count(distinct id) from course_class", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "[null]", "[\"2026-10-01\",null]"})
    void invalidDateListsReturn400WithoutSaving(String days) throws Exception {
        try {
            batch(days, courseId, "08:00", "10:00").andExpect(status().isBadRequest());
        } finally {
            assertEquals(0, count());
        }
    }

    @Test
    void missingDatesReturn400WithoutSaving() throws Exception {
        mvc.perform(post("/api/courseClass/courseClass/batch").contentType(APPLICATION_JSON)
                .content("{\"courseId\":" + courseId + ",\"session\":\"Manhã\",\"start\":\"08:00\",\"finish\":\"10:00\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, count());
    }

    @Test
    void repeatedDateRollsBackEntireBatch() throws Exception {
        batch("[\"2026-10-01\",\"2026-10-01\"]", courseId, "08:00", "10:00")
                .andExpect(status().isConflict());
        assertEquals(0, count());
    }

    @Test
    void laterExistingDateRollsBackEarlierNewDate() throws Exception {
        batch("[\"2026-10-03\"]", courseId, "08:00", "10:00").andExpect(status().isCreated());
        batch("[\"2026-10-01\",\"2026-10-03\"]", courseId, "08:00", "10:00")
                .andExpect(status().isConflict());
        assertEquals(1, count());
        assertEquals(0, jdbc.queryForObject("select count(*) from course_class where class_day = '2026-10-01'", Integer.class));
    }

    @Test
    void rollbackMessageDoesNotClaimEarlierDatesWereSaved() throws Exception {
        var result = batch("[\"2026-10-01\",\"2026-10-01\"]", courseId, "08:00", "10:00")
                .andExpect(status().isConflict()).andReturn();
        assertEquals(0, count());
        String message = JsonPath.read(result.getResponse().getContentAsString(), "$.message");
        assertFalse(message.contains("Foram registradas com sucesso"), "Rollback removes all dates, so partial success is misleading: " + message);
    }

    @Test
    void nonexistentCourseReturns404() throws Exception {
        batch("[\"2026-10-01\"]", Long.MAX_VALUE, "08:00", "10:00").andExpect(status().isNotFound());
        assertEquals(0, count());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void invalidCourseIdReturns400(long id) throws Exception {
        batch("[\"2026-10-01\"]", id, "08:00", "10:00").andExpect(status().isBadRequest());
        assertEquals(0, count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"08:00", "07:00"})
    void equalOrReversedTimesReturn400(String finish) throws Exception {
        batch("[\"2026-10-01\",\"2026-10-02\"]", courseId, "08:00", finish)
                .andExpect(status().isBadRequest());
        assertEquals(0, count());
    }

    @Test
    void malformedDateReturns400() throws Exception {
        batch("[\"invalid\"]", courseId, "08:00", "10:00").andExpect(status().isBadRequest());
        assertEquals(0, count());
    }

    @Test
    void laterScheduleConflictRollsBackEarlierDateForEnrolledPerson() throws Exception {
        long otherId = createCourse("Outro curso");
        batch("[\"2026-10-03\"]", otherId, "09:00", "11:00").andExpect(status().isCreated());
        mvc.perform(post("/api/person").contentType(APPLICATION_JSON).content("""
                {"fullName":"Pessoa lote","cpf":"01234567890","email":"pessoa@example.com",
                 "personalPhone":"11999990000","personalPhoneHasWhatsapp":false,
                 "address":{"street":"Rua Exemplo","number":42,"neighborhood":"Centro"},
                 "gender":"FEMALE","education":"HIGH_SCHOOL_COMPLETE","workState":"ONLY_STUDYING",
                 "disabilities":["HEARING"]}
                """)).andExpect(status().isCreated());
        for (long id : new long[]{courseId, otherId}) {
            mvc.perform(post("/api/register").contentType(APPLICATION_JSON).content("""
                    {"personCpf":"01234567890","courseOfInterestId":%d,"registerDate":"2026-10-01"}
                    """.formatted(id))).andExpect(status().isCreated());
        }
        batch("[\"2026-10-01\",\"2026-10-03\"]", courseId, "08:00", "10:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A aula causa conflito de horários para uma pessoa inscrita."));
        assertEquals(1, count());
        assertEquals(0, jdbc.queryForObject("select count(*) from course_class where course_id = ?", Integer.class, courseId));
    }

    @Test
    void swaggerExposesBatchPostWithJsonBody() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/courseClass/courseClass/batch'].post.requestBody.required").value(true));
    }
}
