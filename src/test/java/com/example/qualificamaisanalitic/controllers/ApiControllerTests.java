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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

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
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(user("api-tests").roles("AGENT"))).build();
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
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(classId))
                .andExpect(jsonPath("$[0].statusClass").value("CANCELED"));
    }

    @Test
    void attendanceUsesClassAndPersonAndUpdatesOnlySelectedCourse() throws Exception {
        long personId = id(createPerson("01234567890", "Pessoa Exemplo"));
        long courseId = id(createCourse("Curso A"));
        long otherCourseId = id(createCourse("Curso B"));
        long classId = id(createClass(courseId));
        long otherClassId = id(createClass(otherCourseId, 14, 16));
        createRegistration("01234567890", courseId);
        createRegistration("01234567890", otherCourseId);
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

    @Test
    void classStatusPatchSupportsPostponingReschedulingAndReactivation() throws Exception {
        long courseId = id(createCourse("Curso A"));
        long classId = id(createClass(courseId).andExpect(jsonPath("$.statusClass").value("ACTIVE")));
        mvc.perform(patch("/api/courseClass").contentType(APPLICATION_JSON)
                        .content("{\"classId\":" + classId + ",\"statusClass\":\"POSTPONED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statusClass").value("POSTPONED"));
        mvc.perform(patch("/api/courseClass").contentType(APPLICATION_JSON).content("""
                {"classId":%d,"day":"2026-10-02","start":"14:00:00","finish":"16:00:00","statusClass":"ACTIVE"}
                """.formatted(classId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.day").value("2026-10-02"))
                .andExpect(jsonPath("$.statusClass").value("ACTIVE"));
        mvc.perform(patch("/api/courseClass").contentType(APPLICATION_JSON)
                        .content("{\"classId\":" + classId + ",\"statusClass\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void attendanceRequiresThePersonsRegistrationInTheClassCourseAndRejectsDuplicates() throws Exception {
        long personId = id(createPerson("01234567890", "Pessoa Exemplo"));
        createPerson("98765432100", "Outra Pessoa");
        long courseId = id(createCourse("Curso da aula"));
        long otherCourseId = id(createCourse("Outro curso"));
        long classId = id(createClass(courseId));
        String body = """
                {"personId":%d,"courseClassId":%d,"status":"PRESENT"}
                """.formatted(personId, classId);

        for (boolean unrelatedRegistrations : new boolean[]{false, true}) {
            if (unrelatedRegistrations) {
                createRegistration("01234567890", otherCourseId);
                createRegistration("98765432100", courseId);
            }
            mvc.perform(post("/api/presence").contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("A pessoa não possui inscrição no curso desta aula."));
            assertEquals(0, jdbc.queryForObject("select count(*) from presence", Integer.class));
        }

        createRegistration("01234567890", courseId);
        mvc.perform(post("/api/presence").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/presence").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("O Aluno(a) já possui uma presença registrada para esta aula."));
        assertEquals(1, jdbc.queryForObject("select count(*) from presence", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/person", "/api/course", "/api/courseClass", "/api/presence", "/api/register"})
    void rejectsMalformedJsonAndMissingBodyWithoutSaving(String route) throws Exception {
        mvc.perform(post(route).contentType(APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post(route).contentType(APPLICATION_JSON)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.message").isNotEmpty());
        for (String table : new String[]{"person", "address", "course", "course_class", "presence", "register"}) {
            assertEquals(0, jdbc.queryForObject("select count(*) from " + table, Integer.class));
        }
    }

    @Test
    void rejectsInvalidPathIdAndUnsupportedMethod() throws Exception {
        mvc.perform(delete("/api/course/course/not-a-number")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(put("/api/course").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.status").value(405))
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")));
        // O contrato atual exige JSON no GET; query parameters ainda não substituem o corpo.
        mvc.perform(get("/api/presence").param("courseId", "1").param("courseClassId", "2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationLifecycleNormalizesCpfReusesPersonAndDeletesOnlySelectedCourse() throws Exception {
        long personId = id(createPerson("01234567890", "Pessoa Exemplo"));
        long courseId = id(createCourse("Curso A"));
        long otherId = id(createCourse("Curso B"));
        long classId = id(createClass(courseId));
        long registerId = id(createRegistration("012.345.678-90", courseId)
                .andExpect(jsonPath("$.person.id").value(personId))
                .andExpect(jsonPath("$.person.cpf").value("01234567890"))
                .andExpect(jsonPath("$.person.address.street").value("Rua Exemplo"))
                .andExpect(jsonPath("$.person.disabilities[0]").value("HEARING"))
                .andExpect(jsonPath("$.courseOfInterest.id").value(courseId))
                .andExpect(jsonPath("$.courseOfInterest.courseClass[0].id").value(classId))
                .andExpect(jsonPath("$.courseOfInterest.courseClass[0].course.courseClass").doesNotExist())
                .andExpect(jsonPath("$.registerDate").value("2026-10-01")));
        long otherRegisterId = id(createRegistration("01234567890abc", otherId));

        mvc.perform(get("/api/register/register/{cpf}", "012.345.678-90"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        String key = registrationKey("012.345.678-90", courseId);
        mvc.perform(get("/api/register").contentType(APPLICATION_JSON).content(key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(registerId));
        mvc.perform(delete("/api/register").contentType(APPLICATION_JSON).content(key))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/register/register/{cpf}", "012.345.678-90"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherRegisterId));
        assertEquals(1, jdbc.queryForObject("select count(*) from person", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from address", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from person_disabilities", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from course", Integer.class));
    }

    @Test
    void registrationListIsEmptyForUnknownCpfAndDoesNotIncludeOtherPeople() throws Exception {
        createPerson("01234567890", "Pessoa A");
        createPerson("98765432100", "Pessoa B");
        long courseId = id(createCourse("Curso A"));
        createRegistration("01234567890", courseId);
        long otherRegisterId = id(createRegistration("98765432100", courseId));

        mvc.perform(get("/api/register/register/111.222.333-44"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/register/register/987.654.321-00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherRegisterId));
    }

    @Test
    void duplicateRegistrationRollsBackWithoutChangingExistingDateOrPerson() throws Exception {
        createPerson("01234567890", "Pessoa Exemplo");
        long courseId = id(createCourse("Curso A"));
        long registerId = id(createRegistration("01234567890", courseId));

        mvc.perform(post("/api/register")
                .contentType(APPLICATION_JSON).content("""
                        {"personCpf":"012.345.678-90","courseOfInterestId":%d,"registerDate":"2026-10-02"}
                        """.formatted(courseId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Já existe um registro com os dados informados."));
        mvc.perform(get("/api/register").contentType(APPLICATION_JSON)
                        .content(registrationKey("01234567890", courseId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(registerId))
                .andExpect(jsonPath("$.registerDate").value("2026-10-01"));
        assertEquals(1, jdbc.queryForObject("select count(*) from register", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from person", Integer.class));
    }

    @Test
    void registrationRejectsUnknownPersonAndCourseWithoutSaving() throws Exception {
        createPerson("01234567890", "Pessoa Exemplo");
        long courseId = id(createCourse("Curso A"));
        for (String payload : new String[]{registrationBody("98765432100", courseId),
                registrationBody("01234567890", -1)}) {
            mvc.perform(post("/api/register").contentType(APPLICATION_JSON).content(payload))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from register", Integer.class));
    }

    @Test
    void missingRegistrationSearchAndDeletionPreserveOtherRegistrations() throws Exception {
        createPerson("01234567890", "Pessoa Exemplo");
        long courseId = id(createCourse("Curso A"));
        createRegistration("01234567890", courseId);
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                get("/api/register"), delete("/api/register")}) {
            mvc.perform(request.contentType(APPLICATION_JSON).content(registrationKey("012.345.678-90", -1)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("Inscrição não encontrada para a pessoa e o curso informados."));
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from register", Integer.class));
    }

    @Test
    void registrationRejectsOverlappingClassesAndAllowsTouchingClasses() throws Exception {
        createPerson("01234567890", "Pessoa Exemplo");
        long existingId = id(createCourse("Curso A"));
        long conflictingId = id(createCourse("Curso B"));
        long touchingId = id(createCourse("Curso C"));
        createClass(existingId);
        createClass(conflictingId, 9, 11);
        createClass(touchingId, 10, 12);
        createRegistration("01234567890", existingId);

        mvc.perform(post("/api/register").contentType(APPLICATION_JSON)
                        .content(registrationBody("012.345.678-90", conflictingId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").isNotEmpty());
        assertEquals(1, jdbc.queryForObject("select count(*) from register", Integer.class));

        createRegistration("012.345.678-90", touchingId);
        mvc.perform(get("/api/register/register/01234567890"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        assertEquals(0, jdbc.queryForObject("select count(*) from register where course_id = ?",
                Integer.class, conflictingId));
    }

    @Test
    void registrationRequiresJsonBodiesAndRejectsInvalidDatesAndCourseIds() throws Exception {
        mvc.perform(get("/api/register").param("personCpf", "01234567890").param("courseOfInterestId", "1"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/register")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/register").contentType(APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/register").contentType(APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest());
        for (String payload : new String[]{
                "{\"personCpf\":\"01234567890\",\"courseOfInterestId\":\"abc\",\"registerDate\":\"2026-10-01\"}",
                "{\"personCpf\":\"01234567890\",\"courseOfInterestId\":1,\"registerDate\":\"invalid\"}"}) {
            mvc.perform(post("/api/register").contentType(APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/register").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        assertEquals(0, jdbc.queryForObject("select count(*) from register", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/person", "/api/courseClass", "/api/presence", "/api/register"})
    void requiredCreateFieldsReturnBadRequestWithoutSaving(String route) throws Exception {
        mvc.perform(post(route).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());
        for (String table : new String[]{"person", "address", "course_class", "presence", "register"}) {
            assertEquals(0, jdbc.queryForObject("select count(*) from " + table, Integer.class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/person", "/api/course", "/api/courseClass", "/api/presence"})
    void patchRequiresItsIdentifiers(String route) throws Exception {
        mvc.perform(patch(route).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void queryAndDeletionRequireTheirBodyFields() throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                get("/api/presence"), get("/api/register"), delete("/api/register")}) {
            mvc.perform(request.contentType(APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/person/person/-1", "/api/course/course/-1", "/api/courseClass/courseClass/-1"})
    void deletingAnUnknownRecordReturnsNotFound(String route) throws Exception {
        mvc.perform(delete(route)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void duplicateCpfAndReferencedCourseReturnConflictWithoutExposingDatabaseDetails() throws Exception {
        createPerson("01234567890", "Pessoa Exemplo");
        mvc.perform(post("/api/person").contentType(APPLICATION_JSON)
                        .content(personBody("01234567890", "Pessoa Duplicada")))
                .andExpect(status().isConflict()).andExpect(content().json("""
                        {"status":409,"message":"Já existe um registro com os dados informados."}
                        """));
        long courseId = id(createCourse("Curso A"));
        createRegistration("01234567890", courseId);
        mvc.perform(delete("/api/course/course/{id}", courseId))
                .andExpect(status().isConflict()).andExpect(content().json("""
                        {"status":409,"message":"A operação conflita com os vínculos entre os registros."}
                        """));
        assertEquals(1, jdbc.queryForObject("select count(*) from course", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from person", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from register", Integer.class));
    }

    @Test
    void classValidationAndDuplicateDayUseDifferentStatuses() throws Exception {
        long courseId = id(createCourse("Curso A"));
        long classId = id(createClass(courseId));
        mvc.perform(patch("/api/courseClass").contentType(APPLICATION_JSON)
                        .content("{\"classId\":%d,\"finish\":\"08:00:00\"}".formatted(classId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/courseClass").contentType(APPLICATION_JSON).content("""
                {"day":"2026-10-01","session":"Tarde","start":"14:00:00","finish":"16:00:00","courseId":%d}
                """.formatted(courseId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertEquals(1, jdbc.queryForObject("select count(*) from course_class", Integer.class));
    }

    @Test
    void classHistoryAndCanceledAttendanceReturnConflictAndPreserveHistory() throws Exception {
        long personId = id(createPerson("01234567890", "Pessoa Exemplo"));
        long otherPersonId = id(createPerson("98765432100", "Outra Pessoa"));
        long courseId = id(createCourse("Curso A"));
        long classId = id(createClass(courseId));
        createRegistration("01234567890", courseId);
        createRegistration("98765432100", courseId);
        mvc.perform(post("/api/presence").contentType(APPLICATION_JSON).content("""
                {"personId":%d,"courseClassId":%d,"status":"PRESENT"}
                """.formatted(personId, classId))).andExpect(status().isCreated());
        mvc.perform(patch("/api/courseClass").contentType(APPLICATION_JSON)
                        .content("{\"classId\":%d,\"day\":\"2026-10-02\"}".formatted(classId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        mvc.perform(delete("/api/courseClass/courseClass/{id}", classId)).andExpect(status().isNoContent());
        mvc.perform(post("/api/presence").contentType(APPLICATION_JSON).content("""
                {"personId":%d,"courseClassId":%d,"status":"PRESENT"}
                """.formatted(otherPersonId, classId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertEquals(1, jdbc.queryForObject("select count(*) from presence", Integer.class));
    }

    @Test
    void invalidCpfAndPhoneReturnReadableValidationErrors() throws Exception {
        mvc.perform(get("/api/person/person/123")).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"status\":400,\"message\":\"CPF deve conter 11 dígitos.\"}"));
        createPerson("01234567890", "Pessoa Exemplo");
        mvc.perform(patch("/api/person").contentType(APPLICATION_JSON)
                        .content("{\"Cpf\":\"01234567890\",\"personalPhone\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unknownRouteAndUnsupportedContentTypeUseTheSameErrorFormat() throws Exception {
        mvc.perform(get("/api/unknown")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/course").contentType("text/plain").content("example"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void classCreationDistinguishesMissingAndUnknownCourseId() throws Exception {
        String body = """
                {"day":"2026-10-02","session":"Tarde","start":"10:00:00","finish":"14:00:00"%s}
                """;
        mvc.perform(post("/api/courseClass").contentType(APPLICATION_JSON).content(body.formatted("")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("O ID do curso é obrigatório."));
        mvc.perform(post("/api/courseClass").contentType(APPLICATION_JSON)
                        .content(body.formatted(",\"courseId\":-1")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Curso não encontrado com o ID: -1"));
        assertEquals(0, jdbc.queryForObject("select count(*) from course_class", Integer.class));
    }

    @Test
    void swaggerDescribesTheCurrentClassCreationContractAndServesItsUi() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.AddCourseClassDto.properties.courseId.type").value("integer"))
                .andExpect(jsonPath("$.components.schemas.AddCourseClassDto.properties.course").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AddCourseClassDto.properties.start.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.AddCourseClassDto.properties.finish.type").value("string"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    private String registrationKey(String cpf, long courseId) {
        return "{\"personCpf\":\"%s\",\"courseOfInterestId\":%d}".formatted(cpf, courseId);
    }

    private String registrationBody(String cpf, long courseId) {
        return """
                {"personCpf":"%s","courseOfInterestId":%d,"registerDate":"2026-10-01"}
                """.formatted(cpf, courseId);
    }

    private ResultActions createRegistration(String cpf, long courseId) throws Exception {
        return mvc.perform(post("/api/register").contentType(APPLICATION_JSON)
                .content(registrationBody(cpf, courseId))).andExpect(status().isCreated());
    }

    private ResultActions createClass(long courseId, int start, int finish) throws Exception {
        return mvc.perform(post("/api/courseClass").contentType(APPLICATION_JSON).content("""
                {"day":"2026-10-01","session":"Sessão","start":"%02d:00:00",
                 "finish":"%02d:00:00","courseId":%d}
                """.formatted(start, finish, courseId))).andExpect(status().isCreated());
    }

    private ResultActions createPerson(String cpf, String name) throws Exception {
        return mvc.perform(post("/api/person").contentType(APPLICATION_JSON).content(personBody(cpf, name)))
                .andExpect(status().isCreated());
    }

    private String personBody(String cpf, String name) {
        return """
                {"fullName":"%s","cpf":"%s","email":"pessoa@example.com",
                 "personalPhone":"(11) 99999-0000","personalPhoneHasWhatsapp":false,
                 "address":{"street":"Rua Exemplo","number":42,"neighborhood":"Centro"},
                 "gender":"FEMALE","education":"HIGH_SCHOOL_COMPLETE","workState":"ONLY_STUDYING",
                 "disabilities":["Auditiva","HEARING","1"]}
                """.formatted(name, cpf);
    }

    private ResultActions createCourse(String name) throws Exception {
        return mvc.perform(post("/api/course").contentType(APPLICATION_JSON).content("""
                {"name":"%s","description":"Introdução","start":"2026-10-01","finish":"2026-11-01"}
                """.formatted(name))).andExpect(status().isCreated());
    }

    private ResultActions createClass(long courseId) throws Exception {
        return mvc.perform(post("/api/courseClass").contentType(APPLICATION_JSON).content("""
                {"day":"2026-10-01","session":"Manhã","start":"08:00:00",
                 "finish":"10:00:00","courseId":%d}
                """.formatted(courseId))).andExpect(status().isCreated());
    }

    private long id(ResultActions response) throws Exception {
        return ((Number) JsonPath.read(response.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }
}
