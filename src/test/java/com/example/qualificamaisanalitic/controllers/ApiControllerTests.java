package com.example.qualificamaisanalitic.controllers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Requisições MVC reais, com serviços, JSON e banco de testes; sem porta HTTP ou transação do teste. */
@SpringBootTest
@ActiveProfiles("test")
class ApiControllerTests {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        clearTestDatabase();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void clearTestDatabase() {
        for (String table : new String[]{"presence", "course_class", "register", "person_disabilities", "person", "address", "course"}) {
            jdbc.update("delete from " + table);
        }
    }

    @Test
    void personLifecycleNormalizesInputAndPreservesOmittedFields() throws Exception {
        var created = createPerson("012.345.678-90", "Pessoa Exemplo")
                .andExpect(jsonPath("$.cpf").value("01234567890"))
                .andExpect(jsonPath("$.personalPhone").value("11999990000"))
                .andExpect(jsonPath("$.address.street").value("Rua Exemplo"))
                .andExpect(jsonPath("$.disabilities[0]").value("HEARING"));
        long id = id(created);
        mvc.perform(get("/api/person/person/{cpf}", "012.345.678-90"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(patch("/api/person").contentType(APPLICATION_JSON).content("""
                {"Cpf":"01234567890","email":"novo@example.com","disabilities":["Visual","4"]}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("novo@example.com"))
                .andExpect(jsonPath("$.personalPhone").value("11999990000"))
                .andExpect(jsonPath("$.disabilities[0]").value("VISUAL"));
        mvc.perform(get("/api/person")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("novo@example.com"));
        mvc.perform(delete("/api/person/person/{id}", id)).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(get("/api/person")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void personListingKeepsAlphabeticalOrder() throws Exception {
        createPerson("12345678901", "Zelia");
        createPerson("01234567890", "Ana");
        mvc.perform(get("/api/person")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Ana"))
                .andExpect(jsonPath("$[1].fullName").value("Zelia"));
    }

    @Test
    void courseLifecycleUsesIdInPatchAndReturnsSavedChanges() throws Exception {
        long id = id(createCourse("Curso Exemplo"));
        mvc.perform(patch("/api/course").contentType(APPLICATION_JSON)
                        .content("{\"courseId\":" + id + ",\"name\":\"Curso Atualizado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Curso Atualizado"))
                .andExpect(jsonPath("$.description").value("Introdução"))
                .andExpect(jsonPath("$.start").value("2026-10-01"));
        mvc.perform(get("/api/course/course/{name}", "Curso Atualizado"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/course")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/api/course/course/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/course")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void classLifecycleFiltersByCourseAndSerializesBidirectionalRelationship() throws Exception {
        long courseId = id(createCourse("Curso com turma"));
        long otherId = id(createCourse("Outro curso"));
        long classId = id(createClass(courseId));
        mvc.perform(patch("/api/courseClass").contentType(APPLICATION_JSON)
                        .content("{\"classId\":" + classId + ",\"session\":\"Tarde\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.session").value("Tarde"))
                .andExpect(jsonPath("$.day").value("2026-10-01"))
                .andExpect(jsonPath("$.course.id").value(courseId));
        mvc.perform(get("/api/courseClass/courseClass/{courseId}", courseId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(classId))
                .andExpect(jsonPath("$[0].course.id").value(courseId))
                .andExpect(jsonPath("$[0].course.courseClass").doesNotExist());
        mvc.perform(get("/api/courseClass/courseClass/{courseId}", otherId))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/course/course/{name}", "Curso com turma"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.courseClass[0].id").value(classId))
                .andExpect(jsonPath("$.courseClass[0].course.id").value(courseId))
                .andExpect(jsonPath("$.courseClass[0].course.courseClass").doesNotExist());
        mvc.perform(get("/api/course")).andExpect(status().isOk());
        mvc.perform(delete("/api/courseClass/courseClass/{id}", classId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/courseClass/courseClass/{courseId}", courseId))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void attendanceUsesClassAndPersonAndUpdatesOnlySelectedCourse() throws Exception {
        long personId = id(createPerson("01234567890", "Pessoa Exemplo"));
        long courseId = id(createCourse("Curso A"));
        long otherCourseId = id(createCourse("Curso B"));
        long classId = id(createClass(courseId));
        long otherClassId = id(createClass(otherCourseId));
        for (long[] selected : new long[][]{{classId, courseId}, {otherClassId, otherCourseId}}) {
            mvc.perform(post("/api/presence").contentType(APPLICATION_JSON).content("""
                    {"personId":%d,"courseClassId":%d,"status":"PRESENT"}
                    """.formatted(personId, selected[0])))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.courseClass.day").value("2026-10-01"))
                    .andExpect(jsonPath("$.courseClass.id").value(selected[0]))
                    .andExpect(jsonPath("$.person.id").value(personId))
                    .andExpect(jsonPath("$.course.id").value(selected[1]));
        }
        mvc.perform(patch("/api/presence").contentType(APPLICATION_JSON).content("""
                {"personId":%d,"courseClassId":%d,"status":"JUSTIFIED"}
                """.formatted(personId, otherClassId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("JUSTIFIED"));
        mvc.perform(get("/api/presence/presence/{personId}", personId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/presence").contentType(APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"courseClassId\":" + classId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PRESENT"));
        mvc.perform(get("/api/presence").contentType(APPLICATION_JSON)
                        .content("{\"courseId\":" + otherCourseId + ",\"courseClassId\":" + otherClassId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("JUSTIFIED"));
        mvc.perform(get("/api/presence").contentType(APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"courseClassId\":" + otherClassId + "}"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/person", "/api/course", "/api/courseClass", "/api/presence"})
    void rejectsMalformedJsonAndMissingBodyWithoutSaving(String route) throws Exception {
        mvc.perform(post(route).contentType(APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(route).contentType(APPLICATION_JSON)).andExpect(status().isBadRequest());
        for (String table : new String[]{"person", "address", "course", "course_class", "presence"}) {
            assertEquals(0, jdbc.queryForObject("select count(*) from " + table, Integer.class));
        }
    }

    @Test
    void rejectsInvalidPathIdAndUnsupportedMethod() throws Exception {
        mvc.perform(delete("/api/course/course/not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/course").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        // O contrato atual exige JSON no GET; query parameters ainda não substituem o corpo.
        mvc.perform(get("/api/presence").param("courseId", "1").param("courseClassId", "2"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions createPerson(String cpf, String name) throws Exception {
        return mvc.perform(post("/api/person").contentType(APPLICATION_JSON).content("""
                {"fullName":"%s","cpf":"%s","email":"pessoa@example.com",
                 "personalPhone":"(11) 99999-0000","personalPhoneHasWhatsapp":false,
                 "address":{"street":"Rua Exemplo","number":42,"neighborhood":"Centro"},
                 "gender":"FEMALE","education":"HIGH_SCHOOL_COMPLETE","workState":"ONLY_STUDYING",
                 "disabilities":["Auditiva","HEARING","1"]}
                """.formatted(name, cpf))).andExpect(status().isCreated());
    }

    private ResultActions createCourse(String name) throws Exception {
        return mvc.perform(post("/api/course").contentType(APPLICATION_JSON).content("""
                {"name":"%s","description":"Introdução","start":"2026-10-01","finish":"2026-11-01"}
                """.formatted(name))).andExpect(status().isCreated());
    }

    private ResultActions createClass(long courseId) throws Exception {
        return mvc.perform(post("/api/courseClass").contentType(APPLICATION_JSON).content("""
                {"day":"2026-10-01","session":"Manhã","start":"2026-10-01T08:00:00",
                 "finish":"2026-10-01T10:00:00","course":{"id":%d}}
                """.formatted(courseId))).andExpect(status().isCreated());
    }

    private long id(ResultActions response) throws Exception {
        return ((Number) JsonPath.read(response.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
