package com.services;

import com.entities.*;
import com.enums.*;
import com.exceptions.ConflictException;
import com.reports.AttendanceSummary;
import com.reports.PdfDocument;
import com.reports.ReportProperties;
import com.repositories.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.utils.ValidationUtils.*;

@Service
@Transactional(readOnly = true)
public class PdfService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final CourseRepository courses;
    private final PersonRepository people;
    private final RegisterRepository registers;
    private final CourseClassRepository classes;
    private final PresenceRepository presences;
    private final ReportProperties properties;
    private final Clock clock;

    public PdfService(CourseRepository courses, PersonRepository people, RegisterRepository registers,
                      CourseClassRepository classes, PresenceRepository presences,
                      ReportProperties properties, Clock clock) {
        this.courses = courses;
        this.people = people;
        this.registers = registers;
        this.classes = classes;
        this.presences = presences;
        this.properties = properties;
        this.clock = clock;
    }

    public byte[] attendanceReport(Long courseId, LocalDate start, LocalDate finish) throws IOException {
        dateRange(start, finish);
        Course course = course(courseId);
        LocalDateTime now = now();
        AttendanceData data = attendanceData(course, start, finish, now);
        try (PdfDocument pdf = document("Relatório de frequência", now)) {
            courseFields(pdf, course);
            pdf.field("Filtro do período", range(start, finish));
            pdf.paragraph("Considera aulas ativas já encerradas, a partir da inscrição. Faltas justificadas não "
                    + "contam como presença. Sem lançamento = pendente; a frequência inclui pendências no total.");
            List<List<String>> rows = new ArrayList<>();
            for (Register register : registers.findByCourseOfInterest_IdOrderByPerson_FullNameAsc(courseId)) {
                AttendanceSummary summary = summarize(data, register.getPerson().getId(), register.getRegisterDate().toLocalDate());
                rows.add(List.of(name(register.getPerson()), register.getStatus().getDescription(),
                        "" + summary.present(), "" + summary.absent(), "" + summary.justified(),
                        "" + summary.pending(), number(summary.percentage()) + "%"));
            }
            pdf.table(List.of("Participante", "Inscrição", "Pres.", "Faltas", "Justif.", "Pend.", "Freq."),
                    new float[]{3.5f, 1.4f, 1, 1, 1, 1, 1.2f}, rows);
            pdf.paragraph("Inscrições canceladas são exibidas para consulta histórica. O cadastro não possui data de cancelamento.");
            return pdf.bytes();
        }
    }

    public byte[] registrationProof(Long registerId) throws IOException {
        Register register = registration(registerId);
        Course course = register.getCourseOfInterest();
        if (register.getStatus() != StatusRegister.ACTIVE || course.getStatusCourse() == StatusCourse.CANCELED) {
            throw new ConflictException("Registration proof requires a valid, active registration in a non-canceled course.");
        }
        try (PdfDocument pdf = document("Comprovante de inscrição", now())) {
            pdf.paragraph("Comprovamos a inscrição do participante abaixo no curso informado.");
            pdf.field("Participante", name(register.getPerson()));
            pdf.field("Inscrição", register.getId());
            pdf.field("Data da inscrição", date(register.getRegisterDate()));
            pdf.field("Situação", register.getStatus().getDescription());
            courseFields(pdf, course);
            schedule(pdf, course, null, null);
            return pdf.bytes();
        }
    }

    public byte[] participantRecord(Long personId) throws IOException {
        Person person = person(personId);
        try (PdfDocument pdf = document("Ficha do participante", now())) {
            pdf.paragraph("Uso interno - acesso restrito à equipe do Qualifica+.");
            pdf.section("Dados cadastrais e contatos");
            pdf.field("Nome completo", person.getFullName());
            pdf.field("Nome social", person.getSocialName());
            pdf.field("CPF", person.getCpf());
            pdf.field("E-mail", person.getEmail());
            pdf.field("Telefone pessoal", person.getPersonalPhone());
            pdf.field("WhatsApp", Boolean.TRUE.equals(person.getPersonalPhoneHasWhatsapp()) ? "Sim" : "Não");
            pdf.field("Telefone familiar", person.getFamilyPhone());
            Address address = person.getAddress();
            pdf.field("Endereço", address == null ? null
                    : address.getStreet() + ", " + address.getNumber() + " - " + address.getNeighborhood());
            pdf.field("Gênero", person.getGender() == null ? null : person.getGender().getDescription());
            pdf.field("Escolaridade", person.getEducation() == null ? null : person.getEducation().getDescription());
            pdf.field("Situação de trabalho", person.getWorkState() == null ? null : person.getWorkState().getDescription());
            pdf.field("Deficiências", person.getDisabilities().stream().map(Disabilities::getDescription)
                    .sorted().collect(Collectors.joining(", ")));
            pdf.section("Inscrições");
            List<List<String>> rows = registers.findByPerson_IdOrderByRegisterDateDesc(personId).stream()
                    .map(register -> List.of(register.getCourseOfInterest().getName(), date(register.getRegisterDate()),
                            register.getStatus().getDescription(), range(register.getCourseOfInterest().getStart(),
                                    register.getCourseOfInterest().getFinish()))).toList();
            pdf.table(List.of("Curso", "Inscrição", "Situação", "Período"), new float[]{3, 1.4f, 1.2f, 2}, rows);
            return pdf.bytes();
        }
    }

    public byte[] enrollmentList(Long courseId, boolean includeContact) throws IOException {
        Course course = course(courseId);
        try (PdfDocument pdf = document("Relação de inscritos", now())) {
            courseFields(pdf, course);
            List<List<String>> rows = new ArrayList<>();
            for (Register register : registers.findByCourseOfInterest_IdOrderByPerson_FullNameAsc(courseId)) {
                List<String> row = new ArrayList<>(List.of(name(register.getPerson()),
                        register.getStatus().getDescription(), date(register.getRegisterDate())));
                if (includeContact) {
                    row.add(value(register.getPerson().getPersonalPhone()));
                    row.add(value(register.getPerson().getEmail()));
                }
                rows.add(row);
            }
            if (includeContact) {
                pdf.paragraph("Uso interno - contém contatos dos participantes.");
                pdf.table(List.of("Participante", "Situação", "Inscrição", "Telefone", "E-mail"),
                        new float[]{2.5f, 1.2f, 1.3f, 1.5f, 2.5f}, rows);
            } else {
                pdf.table(List.of("Participante", "Situação", "Inscrição"), new float[]{4, 1.5f, 1.5f}, rows);
            }
            return pdf.bytes();
        }
    }

    public byte[] courseSchedule(Long courseId, LocalDate start, LocalDate finish) throws IOException {
        dateRange(start, finish);
        Course course = course(courseId);
        try (PdfDocument pdf = document("Cronograma do curso", now())) {
            courseFields(pdf, course);
            pdf.field("Filtro do período", range(start, finish));
            schedule(pdf, course, start, finish);
            return pdf.bytes();
        }
    }

    public byte[] participationHistory(Long personId, LocalDate start, LocalDate finish) throws IOException {
        dateRange(start, finish);
        Person person = person(personId);
        LocalDateTime now = now();
        try (PdfDocument pdf = document("Histórico de participação", now)) {
            pdf.field("Participante", name(person));
            pdf.field("Filtro das aulas", range(start, finish));
            pdf.paragraph("Frequência calculada sobre aulas ativas encerradas após a inscrição. "
                    + "Cursos sem presença confirmada não comprovam participação ou conclusão.");
            List<List<String>> rows = new ArrayList<>();
            for (Register register : registers.findByPerson_IdOrderByRegisterDateDesc(personId)) {
                Course course = register.getCourseOfInterest();
                if (start != null && course.getFinish() != null && course.getFinish().isBefore(start)
                        || finish != null && course.getStart() != null && course.getStart().isAfter(finish)) continue;
                AttendanceSummary summary = summarize(attendanceData(course, start, finish, now),
                        personId, register.getRegisterDate().toLocalDate());
                rows.add(List.of(course.getName(), range(course.getStart(), course.getFinish()),
                        course.getStatusCourse().getDescription() + " / " + register.getStatus().getDescription(),
                        summary.present() + "/" + summary.totalClasses(), "" + summary.pending(),
                        number(summary.percentage()) + "%", hours(summary.attendedMinutes())));
            }
            pdf.table(List.of("Curso", "Período do curso", "Curso / inscrição", "Pres./total", "Pend.", "Freq.", "Horas"),
                    new float[]{2.5f, 2.2f, 1.8f, 1.2f, 0.8f, 1.1f, 1.2f}, rows);
            return pdf.bytes();
        }
    }

    public byte[] participationDeclaration(Long registerId, LocalDate start, LocalDate finish) throws IOException {
        dateRange(start, finish);
        Register register = registration(registerId);
        Course course = register.getCourseOfInterest();
        LocalDateTime now = now();
        AttendanceData data = attendanceData(course, start, finish, now);
        AttendanceSummary summary = summarize(data, register.getPerson().getId(), register.getRegisterDate().toLocalDate());
        List<CourseClass> attended = data.classes().stream()
                .filter(lesson -> !lesson.getDay().isBefore(register.getRegisterDate().toLocalDate()))
                .filter(lesson -> data.status(register.getPerson().getId(), lesson.getId()) == PresenceStatus.PRESENT).toList();
        if (attended.isEmpty()) throw new ConflictException("No confirmed participation in the requested period.");
        try (PdfDocument pdf = document("Declaração de participação", now)) {
            pdf.paragraph("Declaramos que " + name(register.getPerson()) + " participou da atividade "
                    + course.getName() + ", no período de " + range(attended.getFirst().getDay(), attended.getLast().getDay())
                    + ", com carga horária de participação apurada de " + hours(summary.attendedMinutes()) + ".");
            pdf.field("Presenças confirmadas", summary.present());
            pdf.paragraph("Carga horária calculada pelos horários das aulas com presença confirmada.");
            signatories(pdf, course);
            return pdf.bytes();
        }
    }

    public byte[] completionCertificate(Long registerId) throws IOException {
        Register register = registration(registerId);
        Course course = register.getCourseOfInterest();
        LocalDateTime now = now();
        ReportProperties.CourseSettings settings = properties.course(course.getId());
        BigDecimal minimum = settings.getMinimumAttendancePercentage();
        BigDecimal minimumHours = settings.getMinimumAttendedHours();
        if (minimum == null || minimum.signum() < 0 || minimum.compareTo(BigDecimal.valueOf(100)) > 0
                || minimumHours == null || minimumHours.signum() < 0) {
            throw new ConflictException("Configure valid completion criteria for this course before issuing certificates.");
        }
        if (register.getStatus() != StatusRegister.ACTIVE || course.getStatusCourse() != StatusCourse.CLOSED
                || course.getFinish() == null || course.getFinish().isAfter(now.toLocalDate())) {
            throw new ConflictException("Certificates require an active registration and a closed, finished course.");
        }
        List<CourseClass> active = sortedClasses(course).stream()
                .filter(lesson -> lesson.getStatusClass() == StatusClass.ACTIVE).toList();
        if (active.isEmpty() || active.stream().anyMatch(lesson -> !hasEnded(lesson, now))) {
            throw new ConflictException("All active course classes must have ended before certification.");
        }
        AttendanceSummary summary = summarize(attendanceData(course, null, null, now),
                register.getPerson().getId(), LocalDate.MIN);
        boolean belowPercentage = BigDecimal.valueOf(summary.present()).multiply(BigDecimal.valueOf(100))
                .compareTo(minimum.multiply(BigDecimal.valueOf(summary.totalClasses()))) < 0;
        boolean belowHours = BigDecimal.valueOf(summary.attendedMinutes())
                .compareTo(minimumHours.multiply(BigDecimal.valueOf(60))) < 0;
        if (summary.present() == 0 || summary.pending() > 0 || belowPercentage || belowHours) {
            throw new ConflictException("The participant has not met the configured completion criteria or has pending attendance.");
        }
        try (PdfDocument pdf = document("Certificado de conclusão", now)) {
            pdf.section("Certificamos que");
            pdf.paragraph(name(register.getPerson()));
            pdf.paragraph("concluiu o curso " + course.getName() + ", realizado no período de "
                    + range(active.getFirst().getDay(), active.getLast().getDay()) + ", com carga horária de "
                    + hours(summary.totalMinutes()) + ", atendendo aos critérios de conclusão definidos pelo setor.");
            pdf.field("Carga horária frequentada", hours(summary.attendedMinutes()));
            pdf.field("Frequência", number(summary.percentage()) + "%");
            pdf.field("Frequência mínima exigida", number(minimum) + "%");
            pdf.field("Carga horária mínima de participação", number(minimumHours) + " h");
            signatories(pdf, course);
            return pdf.bytes();
        }
    }

    private AttendanceData attendanceData(Course course, LocalDate start, LocalDate finish, LocalDateTime now) {
        List<CourseClass> eligible = sortedClasses(course).stream()
                .filter(lesson -> lesson.getStatusClass() == StatusClass.ACTIVE && hasEnded(lesson, now))
                .filter(lesson -> inRange(lesson.getDay(), start, finish)).toList();
        Map<Long, Map<Long, PresenceStatus>> statuses = new HashMap<>();
        for (Presence presence : presences.findByCourseClass_Course_Id(course.getId())) {
            if (presence.getStatus() != null) statuses.computeIfAbsent(presence.getPerson().getId(), id -> new HashMap<>())
                    .put(presence.getCourseClass().getId(), presence.getStatus());
        }
        return new AttendanceData(eligible, statuses);
    }

    private AttendanceSummary summarize(AttendanceData data, Long personId, LocalDate registrationDate) {
        int present = 0, absent = 0, justified = 0, pending = 0;
        long attendedMinutes = 0, totalMinutes = 0;
        for (CourseClass lesson : data.classes()) {
            if (lesson.getDay().isBefore(registrationDate)) continue;
            long minutes = Duration.between(lesson.getStart(), lesson.getFinish()).toMinutes();
            if (minutes <= 0) throw new ConflictException("Class duration must be positive to calculate report hours.");
            totalMinutes += minutes;
            PresenceStatus status = data.status(personId, lesson.getId());
            if (status == PresenceStatus.PRESENT) {
                present++;
                attendedMinutes += minutes;
            } else if (status == PresenceStatus.ABSENT) absent++;
            else if (status == PresenceStatus.JUSTIFIED) justified++;
            else pending++;
        }
        return new AttendanceSummary(present, absent, justified, pending, attendedMinutes, totalMinutes);
    }

    private void schedule(PdfDocument pdf, Course course, LocalDate start, LocalDate finish) throws IOException {
        pdf.section("Aulas e horários");
        List<List<String>> rows = sortedClasses(course).stream().filter(lesson -> inRange(lesson.getDay(), start, finish))
                .map(lesson -> List.of(date(lesson.getDay()), lesson.getStart().format(TIME) + " - "
                        + lesson.getFinish().format(TIME), lesson.getStatusClass().getDescription())).toList();
        pdf.table(List.of("Data", "Horário", "Situação"), new float[]{1.3f, 1.6f, 1.3f}, rows);
    }

    private List<CourseClass> sortedClasses(Course course) {
        return classes.findByCourse(course).stream()
                .sorted(Comparator.comparing(CourseClass::getDay).thenComparing(CourseClass::getStart)).toList();
    }

    private boolean hasEnded(CourseClass lesson, LocalDateTime now) {
        return !lesson.getDay().atTime(lesson.getFinish()).isAfter(now);
    }

    private boolean inRange(LocalDate day, LocalDate start, LocalDate finish) {
        return (start == null || !day.isBefore(start)) && (finish == null || !day.isAfter(finish));
    }

    private Course course(Long id) {
        positiveId(id, "courseId");
        return courses.findById(id).orElseThrow(() -> new EntityNotFoundException("Course not found."));
    }

    private Person person(Long id) {
        positiveId(id, "personId");
        return people.findById(id).orElseThrow(() -> new EntityNotFoundException("Person not found."));
    }

    private Register registration(Long id) {
        positiveId(id, "registerId");
        return registers.findById(id).orElseThrow(() -> new EntityNotFoundException("Registration not found."));
    }

    private void signatories(PdfDocument pdf, Course course) throws IOException {
        List<String> configured = properties.course(course.getId()).getSignatories();
        if (configured == null) return;
        List<String> signatories = configured.stream().filter(Objects::nonNull).filter(name -> !name.isBlank()).toList();
        if (signatories.isEmpty()) return;
        pdf.section("Responsáveis");
        for (String signatory : signatories) {
            pdf.paragraph("________________________________________");
            pdf.paragraph(signatory);
        }
    }

    private void courseFields(PdfDocument pdf, Course course) throws IOException {
        pdf.field("Curso", course.getName());
        pdf.field("Período do curso", range(course.getStart(), course.getFinish()));
        pdf.field("Situação do curso", course.getStatusCourse().getDescription());
    }

    private PdfDocument document(String title, LocalDateTime now) throws IOException {
        return new PdfDocument(properties.getOrganization(), title, now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
    }

    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(properties.getZone())); }
    private String name(Person person) {
        return person.getSocialName() == null || person.getSocialName().isBlank() ? person.getFullName()
                : person.getSocialName() + " (" + person.getFullName() + ")";
    }
    private String date(LocalDate date) { return date == null ? "Não informado" : date.format(DATE); }
    private String date(LocalDateTime dateTime) { return date(dateTime == null ? null : dateTime.toLocalDate()); }
    private String range(LocalDate start, LocalDate finish) {
        return (start == null ? "Início não limitado" : date(start)) + " a "
                + (finish == null ? "Fim não limitado" : date(finish));
    }
    private String value(String value) { return value == null || value.isBlank() ? "Não informado" : value; }
    private String number(BigDecimal value) { return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ','); }
    private String hours(long minutes) { return (minutes / 60) + " h " + (minutes % 60) + " min"; }

    private record AttendanceData(List<CourseClass> classes, Map<Long, Map<Long, PresenceStatus>> statuses) {
        PresenceStatus status(Long personId, Long classId) {
            return statuses.getOrDefault(personId, Map.of()).get(classId);
        }
    }
}
