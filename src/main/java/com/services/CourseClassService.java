package com.services;

import com.dtos.courseClassesDtos.AddCourseClassDto;
import com.dtos.courseClassesDtos.UpdateCourseClassDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.enums.StatusClass;
import com.mappers.CourseClassMapper;
import com.repositories.*;
import jakarta.persistence.EntityNotFoundException;
import com.exceptions.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.utils.RegisterUtils.hasScheduleConflict;

@Service
public class CourseClassService {
    private final CourseClassRepository courseClassRepository;
    private final CourseRepository courseRepository;
    private final CourseClassMapper mapper;
    private final PersonRepository personRepository;
    private final RegisterRepository registerRepository;
    private final PresenceRepository presenceRepository;

    public CourseClassService(CourseClassRepository courseClassRepository, CourseRepository courseRepository,
                              CourseClassMapper mapper, PersonRepository personRepository,
                              RegisterRepository registerRepository, PresenceRepository presenceRepository) {
        this.courseClassRepository = courseClassRepository;
        this.courseRepository = courseRepository;
        this.mapper = mapper;
        this.personRepository = personRepository;
        this.registerRepository = registerRepository;
        this.presenceRepository = presenceRepository;
    }

    @Transactional
    public CourseClass addCourseClass(AddCourseClassDto dto) {
        validateTimes(dto.day(), dto.session(), dto.start(), dto.finish());
        if (dto.courseId() == null) {
            throw new IllegalArgumentException("O ID do curso é obrigatório.");
        }
        Course course2 = courseRepository.findByid(dto.courseId())
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado com o ID: " + dto.courseId()));
        Course course = lockCourse(course2);
        if (courseClassRepository.existsByCourseAndDay(course, dto.day())) {
            throw new ConflictException("O curso já possui uma aula registrada para este dia.");
        }
        CourseClass courseClass = new CourseClass(dto.day(), dto.session(), dto.start(), dto.finish(), course, StatusClass.ACTIVE);
        validateRegistrations(courseClass);
        return courseClassRepository.save(courseClass);
    }

    @Transactional
    public CourseClass updateCourseClass(UpdateCourseClassDto dto) {
        CourseClass courseClass = lockClass(dto.classId(), dto.course());
        Course course = dto.course() != null ? lockCourse(dto.course()) : courseClass.getCourse();
        LocalDate day = dto.day() != null ? dto.day() : courseClass.getDay();
        LocalTime start = dto.start() != null ? dto.start() : courseClass.getStart();
        LocalTime finish = dto.finish() != null ? dto.finish() : courseClass.getFinish();
        String session = dto.session() != null ? dto.session() : courseClass.getSession();
        StatusClass status = dto.statusClass() != null ? dto.statusClass() : courseClass.getStatusClass();
        validateTimes(day, session, start, finish);
        if (courseClassRepository.existsByCourseAndDayAndIdNot(course, day, dto.classId())) {
            throw new ConflictException("O curso já possui uma aula registrada para este dia.");
        }
        boolean changedSchedule = !Objects.equals(course.getId(), courseClass.getCourse().getId())
                || !day.equals(courseClass.getDay()) || !start.equals(courseClass.getStart()) || !finish.equals(courseClass.getFinish());
        if (changedSchedule && presenceRepository.existsByCourseClassId(dto.classId())) {
            throw new ConflictException("Não é possível alterar curso, dia ou horários de uma aula com presenças registradas.");
        }
        CourseClass changedClass = new CourseClass(day, session, start, finish, course, status);
        changedClass.setId(dto.classId());
        validateRegistrations(changedClass);
        mapper.updateCourseClassfromDto(dto, courseClass);
        courseClass.setCourse(course);
        return courseClassRepository.save(courseClass);
    }

    @Transactional
    public void deleteCourseClass(Long id) {
        CourseClass courseClass = lockClass(id, null);
        courseClass.setStatusClass(StatusClass.CANCELED);
        courseClassRepository.save(courseClass);
    }

    public List<CourseClass> allClassesByCourseId(Long courseId) {
        Course course = courseRepository.findByid(courseId)
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado com o ID: " + courseId));
        return courseClassRepository.findByCourse(course);
    }

    private void validateTimes(LocalDate day, String session, LocalTime start, LocalTime finish) {
        if (day == null || session == null || session.isBlank() || start == null || finish == null) {
            throw new IllegalArgumentException("Dia, sessão e horários da aula são obrigatórios.");
        }
        if (!start.isBefore(finish)) {
            throw new IllegalArgumentException("O horário de início deve ser anterior ao horário de fim.");
        }
    }

    private Course lockCourse(Course course) {
        if (course == null || course.getId() == null) {
            throw new IllegalArgumentException("O curso da aula é obrigatório.");
        }
        return courseRepository.findByIdForUpdate(course.getId())
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado"));
    }

    private CourseClass lockClass(Long id, Course replacement) {
        if (id == null) {
            throw new IllegalArgumentException("O ID da aula é obrigatório.");
        }
        Long currentCourseId = courseClassRepository.findCourseIdById(id)
                .orElseThrow(() -> new EntityNotFoundException("Turma não encontrada com o ID: " + id));
        if (replacement != null && replacement.getId() == null) {
            throw new IllegalArgumentException("O curso da aula é obrigatório.");
        }
        Stream.of(currentCourseId, replacement == null ? currentCourseId : replacement.getId())
                .distinct().sorted().forEach(courseId -> courseRepository.findByIdForUpdate(courseId)
                        .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado")));
        CourseClass courseClass = courseClassRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Turma não encontrada com o ID: " + id));
        if (!currentCourseId.equals(courseClass.getCourse().getId())) {
            throw new ConflictException("O curso da aula foi alterado por outra operação. Tente novamente.");
        }
        return courseClass;
    }

    private void validateRegistrations(CourseClass courseClass) {
        if (courseClass.getStatusClass() != StatusClass.ACTIVE) {
            return;
        }
        List<String> cpfs = registerRepository.findPersonCpfsByCourseId(courseClass.getCourse().getId());
        for (String cpf : cpfs) {
            personRepository.findByCpfForUpdate(cpf)
                    .orElseThrow(() -> new EntityNotFoundException("Pessoa inscrita não encontrada"));
        }
        for (String cpf : cpfs) {
            if (hasScheduleConflict(registerRepository, cpf, courseClass)) {
                throw new ConflictException("A aula causa conflito de horários para uma pessoa inscrita.");
            }
        }
    }
}
