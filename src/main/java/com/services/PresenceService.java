package com.services;

import com.dtos.presenceDtos.AddPresenceDto;
import com.dtos.presenceDtos.PresenceByDayAndCourseDto;
import com.dtos.presenceDtos.PresenceUpdateDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Person;
import com.entities.Presence;
import com.repositories.CourseClassRepository;
import com.repositories.CourseRepository;
import com.enums.StatusClass;
import com.repositories.PersonRepository;
import com.repositories.PresenceRepository;
import com.repositories.RegisterRepository;
import jakarta.persistence.EntityNotFoundException;
import com.exceptions.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PresenceService {

    private final PresenceRepository presenceRepository;
    private final PersonRepository personRepository;
    private final CourseClassRepository courseClassRepository;
    private final RegisterRepository repository;
    private final CourseRepository courseRepository;


    public PresenceService(PresenceRepository presenceRepository, PersonRepository personRepository, CourseClassRepository courseClassRepository, RegisterRepository repository, CourseRepository courseRepository) {
        this.presenceRepository = presenceRepository;
        this.personRepository = personRepository;
        this.courseClassRepository = courseClassRepository;
        this.repository = repository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public Presence addPresence(AddPresenceDto dto) {
        if (dto.personId() == null || dto.courseClassId() == null || dto.status() == null) {
            throw new IllegalArgumentException("Pessoa, aula e status da presença são obrigatórios.");
        }
        Long courseId = courseClassRepository.findCourseIdById(dto.courseClassId())
                .orElseThrow(() -> new EntityNotFoundException("Aula não encontrada"));
        courseRepository.findByIdForRegistration(courseId)
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado"));
        Person person = personRepository.findByIdForUpdate(dto.personId())
                .orElseThrow(() -> new EntityNotFoundException("Pessoa não encontrada"));
        CourseClass courseClass = courseClassRepository.findById(dto.courseClassId())
                .orElseThrow(() -> new EntityNotFoundException("Aula não encontrada"));
        Course course = courseClass.getCourse();
        if (!courseId.equals(course.getId())) {
            throw new ConflictException("O curso da aula foi alterado por outra operação. Tente novamente.");
        }
        if (courseClass.getStatusClass() != StatusClass.ACTIVE) {
            throw new ConflictException("Não é possível registrar presença em aula cancelada ou adiada.");
        }
        if (!repository.existsByPersonAndCourseOfInterest(person, course)) {
            throw new ConflictException("A pessoa não possui inscrição no curso desta aula.");
        }

        if (presenceRepository.existsByPersonAndCourseClass(person, courseClass)) {
            throw new ConflictException("O Aluno(a) já possui uma presença registrada para esta aula.");
        }
        Presence presence = new Presence(person, courseClass, course, dto.status());
        return presenceRepository.save(presence);
    }

    @Transactional
    public Presence updatePresence (PresenceUpdateDto update){
        if (update.personId() == null || update.courseClassId() == null || update.status() == null) {
            throw new IllegalArgumentException("Pessoa, aula e status da presença são obrigatórios.");
        }
        Presence presence = presenceRepository.findByCourseClassIdAndPersonId(update.courseClassId(), update.personId())
                .orElseThrow(() -> new EntityNotFoundException("Presença não encontrada"));
        presence.setStatus(update.status());
        return presenceRepository.save(presence);
    }

    public List<Presence> getPresenceByPerson(Long id){
        return presenceRepository.findByPersonId(id);
    }

    public List<Presence> getPresenceByDateAndCourse(PresenceByDayAndCourseDto dayAndCourse){
        if (dayAndCourse.courseId() == null || dayAndCourse.courseClassId() == null) {
            throw new IllegalArgumentException("Curso e aula são obrigatórios para consultar presenças.");
        }
        return presenceRepository.findByCourseClassIdAndCourseId(dayAndCourse.courseClassId(), dayAndCourse.courseId());
    }
}
