package com.services;

import com.dtos.presenceDtos.AddPresenceDto;
import com.dtos.presenceDtos.PresenceByDayAndCourseDto;
import com.dtos.presenceDtos.PresenceUpdateDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Person;
import com.entities.Presence;
import com.repositories.CourseClassRepository;
import com.repositories.PersonRepository;
import com.repositories.PresenceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PresenceService {

    private final PresenceRepository presenceRepository;
    private final PersonRepository personRepository;
    private final CourseClassRepository courseClassRepository;


    public PresenceService(PresenceRepository presenceRepository, PersonRepository personRepository, CourseClassRepository courseClassRepository) {
        this.presenceRepository = presenceRepository;
        this.personRepository = personRepository;
        this.courseClassRepository = courseClassRepository;
    }

    @Transactional
    public Presence addPresence(AddPresenceDto dto) {
        Person person = personRepository.findById(dto.personId())
                .orElseThrow(() -> new EntityNotFoundException("Pessoa não encontrada"));
        CourseClass courseClass = courseClassRepository.findById(dto.courseClassId())
                .orElseThrow(() -> new EntityNotFoundException("Aula não encontrada"));
        if (presenceRepository.existsByPersonAndCourseClass(person, courseClass)) {
            throw new IllegalArgumentException("O Aluno(a) já possui uma presença registrada para esta aula.");
        }
        Course course = courseClass.getCourse();
        Presence presence = new Presence(person, courseClass, course, dto.status());
        return presenceRepository.save(presence);
    }

    @Transactional
    public Presence updatePresence (PresenceUpdateDto update){
        Presence presence = presenceRepository.findByCourseClassIdAndPersonId(update.courseClassId(), update.personId())
                .orElseThrow(() -> new EntityNotFoundException("Presença não encontrada"));
        presence.setStatus(update.status());
        return presenceRepository.save(presence);
    }

    public List<Presence> getPresenceByPerson(Long id){
        return presenceRepository.findByPersonId(id);
    }

    public List<Presence> getPresenceByDateAndCourse(PresenceByDayAndCourseDto dayAndCourse){
        return presenceRepository.findByCourseClassIdAndCourseId(dayAndCourse.courseClassId(), dayAndCourse.courseId());
    }
}
