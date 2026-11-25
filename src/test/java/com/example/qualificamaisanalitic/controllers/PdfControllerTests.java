package com.example.qualificamaisanalitic.controllers;

import com.entities.*;
import com.enums.*;
import com.enums.Period;
import com.example.qualificamaisanalitic.PersonTestData;
import com.reports.PdfDocument;
import com.reports.ReportProperties;
import com.repositories.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import javax.imageio.ImageIO;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.reports.courses.9999.minimum-attendance-percentage=80",
        "app.reports.courses.9999.signatories[0]=Configured coordinator"
})
@ActiveProfiles("test")
@Import(PdfControllerTests.TimeConfiguration.class)
@Transactional
class PdfControllerTests {
    @Autowired private WebApplicationContext context;
    @Autowired private CourseRepository courses;
    @Autowired private PersonRepository people;
    @Autowired private RegisterRepository registers;
    @Autowired private CourseClassRepository classes;
    @Autowired private PresenceRepository presences;
    @Autowired private ReportProperties properties;
    private MockMvc mvc;
    private Course course;
    private Person person;
    private Register register;
    private List<CourseClass> lessons;

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary
        Clock reportClock() { return Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), ZoneOffset.UTC); }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        course = courses.save(new Course("Informática básica", "Curso de teste", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 10), StatusCourse.ACTIVE, 20));
        person = PersonTestData.person("98765432109");
        person.setFullName("João da Conceição");
        person = people.save(person);
        register = registers.save(new Register(person, course, LocalDate.of(2026, 11, 1).atStartOfDay(), StatusRegister.ACTIVE));
        lessons = new ArrayList<>();
        for (int day = 2; day <= 5; day++) lessons.add(lesson(day, StatusClass.ACTIVE));
        mark(0, PresenceStatus.PRESENT);
        mark(1, PresenceStatus.ABSENT);
        mark(2, PresenceStatus.JUSTIFIED);
        ReportProperties.CourseSettings settings = new ReportProperties.CourseSettings();
        settings.setSignatories(List.of("Maria Exemplo - Coordenação", "José Exemplo - Instrutor"));
        settings.setMinimumAttendancePercentage(BigDecimal.valueOf(75));
        properties.getCourses().put(course.getId(), settings);
    }

    @AfterEach
    void resetConfiguration() { properties.getCourses().remove(course.getId()); }

    @ParameterizedTest
    @ValueSource(strings = {"attendance", "proof", "record", "enrollments", "schedule", "history", "declaration", "certificate"})
    void shouldDownloadAllEightReports(String type) throws Exception {
        if (type.equals("certificate")) completeAttendance();
        byte[] bytes = request(route(type)).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages() > 0);
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Informática básica"), text);
            assertTrue(text.contains("Qualifica+"));
            assertFalse(text.contains("null"));
            Path output = Path.of("target", "pdf-qa");
            Files.createDirectories(output);
            Files.write(output.resolve(type + ".pdf"), bytes);
            ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0, 100), "png", output.resolve(type + ".png").toFile());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"attendance", "proof", "record", "enrollments", "schedule", "history", "declaration", "certificate"})
    void shouldRequireAuthenticationForEveryReport(String type) throws Exception {
        mvc.perform(get(route(type)).accept("application/pdf")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"attendance", "proof", "record", "enrollments", "schedule", "history", "declaration", "certificate"})
    void shouldRestrictEveryReportToStaff(String type) throws Exception {
        mvc.perform(get(route(type)).accept("application/pdf").with(user("participant").roles("PARTICIPANT")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void shouldAllowAdministratorsToReadParticipantRecords() throws Exception {
        mvc.perform(get(route("record")).with(user("administrator").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldSeparateAbsencesJustificationsAndPendingAttendance() throws Exception {
        lesson(6, StatusClass.CANCELED);
        lesson(7, StatusClass.POSTPONED);
        lesson(17, StatusClass.ACTIVE);
        String text = reportText(route("attendance"));
        assertTrue(text.contains("João da Conceição"));
        assertTrue(text.contains("25,00%"), text);
        assertTrue(text.replaceAll("\\s+", " ").contains("Ativa 1 1 1 1 25,00%"), text);
    }

    @Test
    void shouldApplyInclusiveDateFiltersAndExcludeClassesBeforeRegistration() throws Exception {
        String text = reportText(route("attendance") + "&start=2026-11-02&finish=2026-11-02");
        assertTrue(text.contains("100,00%"), text);
        register.setRegisterDate(LocalDate.of(2026, 11, 3).atStartOfDay());
        registers.saveAndFlush(register);
        text = reportText(route("attendance"));
        assertTrue(text.contains("0,00%"), text);
        assertFalse(text.contains("25,00%"));
    }

    @Test
    void shouldCalculateHoursOnlyFromConfirmedParticipation() throws Exception {
        String text = reportText(route("declaration"));
        assertTrue(text.contains("2 h 15 min"), text);
        assertTrue(text.replaceAll("\\s+", " ").contains("02/11/2026 a 02/11/2026"), text);
        assertTrue(text.contains("Maria Exemplo"));
        String history = reportText(route("history"));
        assertTrue(history.contains("2 h 15 min"), history);
        assertTrue(history.contains("25,00%"), history);
    }

    @Test
    void shouldUseLocalTimeAndWaitUntilTodaysClassHasEnded() throws Exception {
        CourseClass today = lesson(16, StatusClass.ACTIVE);
        today.setStart(LocalTime.of(12, 0));
        today.setFinish(LocalTime.of(13, 0));
        classes.saveAndFlush(today);
        assertTrue(reportText(route("attendance")).contains("25,00%"));
        today.setStart(LocalTime.of(10, 0));
        today.setFinish(LocalTime.of(11, 0));
        classes.saveAndFlush(today);
        assertTrue(reportText(route("attendance")).contains("20,00%"));
    }

    @Test
    void shouldOmitContactDetailsUnlessRequested() throws Exception {
        String text = reportText(route("enrollments"));
        assertFalse(text.contains(person.getCpf()));
        assertFalse(text.contains(person.getEmail()));
        assertFalse(text.contains(person.getPersonalPhone()));
        text = reportText(route("enrollments") + "?includeContact=true");
        assertTrue(text.contains(person.getEmail()));
        assertTrue(text.contains(person.getPersonalPhone()));
        assertFalse(text.contains(person.getCpf()));
        assertTrue(reportText(route("record")).contains(person.getCpf()));
    }

    @Test
    void shouldIncludeCanceledEnrollmentsAndAllClassStatusesInLists() throws Exception {
        register.setStatus(StatusRegister.CANCELED);
        registers.saveAndFlush(register);
        assertTrue(reportText(route("enrollments")).contains("Cancelada"));
        lesson(6, StatusClass.CANCELED);
        lesson(7, StatusClass.POSTPONED);
        String text = reportText(route("schedule"));
        assertTrue(text.contains("Cancelada"));
        assertTrue(text.contains("Adiada"));
        assertTrue(text.contains("08:00 - 10:15"));
        request(route("proof")).andExpect(status().isConflict());
    }

    @Test
    void shouldRejectInvalidPeriodsIdsAndMissingResources() throws Exception {
        request(route("attendance") + "&start=2026-11-05&finish=2026-11-01").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
        request(route("attendance") + "&start=invalid").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
        request("/api/reports/attendance?courseId=0").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
        request("/api/reports/attendance?courseId=999999999").andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.message").value("Course not found."));
        request("/api/reports/participants/999999999/record").andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
        request("/api/reports/registrations/999999999/proof").andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void shouldReturnEmptyReportsWithoutDividingByZero() throws Exception {
        String text = reportText(route("attendance") + "&start=2026-12-01");
        assertTrue(text.contains("0,00%"));
        text = reportText(route("schedule") + "?start=2026-12-01");
        assertTrue(text.contains("Nenhum registro"));
        request(route("declaration") + "?start=2026-12-01").andExpect(status().isConflict());
    }

    @Test
    void shouldGenerateProofAndScheduleWithoutCourseSettings() throws Exception {
        properties.getCourses().remove(course.getId());
        for (String type : List.of("proof", "schedule")) {
            String text = reportText(route(type));
            assertTrue(text.contains(course.getName()));
            assertTrue(text.contains("08:00 - 10:15"));
            assertFalse(text.contains("Local"));
        }
    }

    @Test
    void shouldRequireExplicitCompletionCriteria() throws Exception {
        properties.getCourses().remove(course.getId());
        request(route("declaration")).andExpect(status().isOk());
        request(route("certificate")).andExpect(status().isConflict());
        assertEquals(0, BigDecimal.valueOf(80).compareTo(properties.course(9999L).getMinimumAttendancePercentage()));
        assertEquals(List.of("Configured coordinator"), properties.course(9999L).getSignatories());
    }

    @ParameterizedTest
    @ValueSource(strings = {"declaration", "certificate"})
    void shouldGenerateDeclarationsAndCertificatesWithoutSignatories(String type) throws Exception {
        if (type.equals("certificate")) completeAttendance();
        for (List<String> signatories : Arrays.asList(null, List.<String>of(), Arrays.asList(null, "", " "))) {
            properties.course(course.getId()).setSignatories(signatories);
            String text = reportText(route(type));
            assertTrue(text.contains(person.getFullName()));
            assertFalse(text.contains("Responsáveis"));
            assertFalse(text.contains("________________________________________"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"declaration", "certificate"})
    void shouldIncludeConfiguredSignatories(String type) throws Exception {
        if (type.equals("certificate")) completeAttendance();
        properties.course(course.getId()).setSignatories(Arrays.asList(null, " ", "Configured coordinator"));
        String text = reportText(route(type));
        assertTrue(text.contains("Responsáveis"));
        assertTrue(text.contains("Configured coordinator"));
    }

    @Test
    void shouldBlockCertificatesForOpenCoursesPendingAttendanceAndInsufficientFrequency() throws Exception {
        request(route("certificate")).andExpect(status().isConflict());
        course.setStatusCourse(StatusCourse.CLOSED);
        courses.saveAndFlush(course);
        request(route("certificate")).andExpect(status().isConflict());
        mark(3, PresenceStatus.ABSENT);
        request(route("certificate")).andExpect(status().isConflict());
        completeAttendance();
        String text = reportText(route("certificate"));
        assertTrue(text.contains("9 h 0 min"), text);
        assertTrue(text.contains("100,00%"));
        assertTrue(text.replaceAll("\\s+", " ").contains("02/11/2026 a 05/11/2026"));
    }

    @Test
    void shouldUseExactThresholdAndRequiredHoursWithoutRoundingUpEligibility() throws Exception {
        completeAttendance();
        presences.findByCourseClassIdAndPersonId(lessons.getFirst().getId(), person.getId()).ifPresent(presence -> {
            presence.setStatus(PresenceStatus.ABSENT);
            presences.saveAndFlush(presence);
        });
        request(route("certificate")).andExpect(status().isOk());
        properties.course(course.getId()).setMinimumAttendancePercentage(new BigDecimal("75.001"));
        request(route("certificate")).andExpect(status().isConflict());
        properties.course(course.getId()).setMinimumAttendancePercentage(BigDecimal.valueOf(75));
        properties.course(course.getId()).setMinimumAttendedHours(new BigDecimal("6.751"));
        request(route("certificate")).andExpect(status().isConflict());
    }

    @Test
    void shouldBlockCertificatesForCanceledEnrollmentOrFutureClasses() throws Exception {
        completeAttendance();
        register.setStatus(StatusRegister.CANCELED);
        registers.saveAndFlush(register);
        request(route("certificate")).andExpect(status().isConflict());
        register.setStatus(StatusRegister.ACTIVE);
        registers.saveAndFlush(register);
        lesson(17, StatusClass.ACTIVE);
        request(route("certificate")).andExpect(status().isConflict());
    }

    @Test
    void shouldAssessTheWholeCourseForLateEnrollmentCertification() throws Exception {
        completeAttendance();
        register.setRegisterDate(LocalDate.of(2026, 11, 5).atStartOfDay());
        registers.saveAndFlush(register);
        presences.deleteAll();
        presences.flush();
        mark(3, PresenceStatus.PRESENT);
        assertTrue(reportText(route("attendance")).contains("100,00%"));
        request(route("certificate")).andExpect(status().isConflict());
    }

    @Test
    void shouldWrapLongCellsRepeatHeadingsAndPreserveEveryRowAcrossPages() throws Exception {
        byte[] bytes;
        try (PdfDocument pdf = new PdfDocument("Qualifica+", "Relação de inscritos", "16/11/2026")) {
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("Nome extenso ".repeat(400), "email".repeat(150) + "@example.com"));
            for (int i = 0; i < 90; i++) rows.add(List.of("Participante " + i + " João", "contato" + i + "@example.com"));
            pdf.table(List.of("Participante", "Contato"), new float[]{1, 1}, rows);
            bytes = pdf.bytes();
        }
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages() > 2);
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Participante 89 João"));
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                assertTrue(stripper.getText(pdf).contains("Contato"));
            }
            Path output = Path.of("target", "pdf-qa");
            Files.createDirectories(output);
            Files.write(output.resolve("multipage.pdf"), bytes);
            ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(1, 100), "png", output.resolve("multipage.png").toFile());
        }
    }

    private CourseClass lesson(int day, StatusClass status) {
        return classes.saveAndFlush(new CourseClass(LocalDate.of(2026, 11, day), Period.MORNING,
                LocalTime.of(8, 0), LocalTime.of(10, 15), course, status));
    }

    private void mark(int index, PresenceStatus status) {
        presences.saveAndFlush(new Presence(person, lessons.get(index), course, status));
    }

    private void completeAttendance() {
        course.setStatusCourse(StatusCourse.CLOSED);
        courses.saveAndFlush(course);
        presences.deleteAll();
        presences.flush();
        for (int i = 0; i < lessons.size(); i++) mark(i, PresenceStatus.PRESENT);
    }

    private ResultActions request(String route) throws Exception {
        return mvc.perform(get(route).accept("application/pdf").with(user("staff").roles("AGENT")));
    }

    private String reportText(String route) throws Exception {
        byte[] bytes = request(route).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument pdf = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(pdf); }
    }

    private String route(String type) {
        return "/api/reports/" + switch (type) {
            case "attendance" -> "attendance?courseId=" + course.getId();
            case "proof" -> "registrations/" + register.getId() + "/proof";
            case "record" -> "participants/" + person.getId() + "/record";
            case "enrollments" -> "courses/" + course.getId() + "/enrollments";
            case "schedule" -> "courses/" + course.getId() + "/schedule";
            case "history" -> "participants/" + person.getId() + "/history";
            case "declaration" -> "registrations/" + register.getId() + "/declaration";
            case "certificate" -> "registrations/" + register.getId() + "/certificate";
            default -> throw new IllegalArgumentException(type);
        };
    }
}
