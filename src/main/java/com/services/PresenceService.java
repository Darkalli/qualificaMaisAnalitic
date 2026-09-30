package com.services;

import com.dtos.presenceDtos.AddPresenceDto;
import com.dtos.presenceDtos.PresenceByDayAndCourseDto;
import com.dtos.presenceDtos.PresenceUpdateDto;
import com.entities.Course;
import com.entities.Person;
import com.entities.Presence;
import com.repositories.CourseRepository;
import com.repositories.PersonRepository;
import com.repositories.PresenceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PresenceService {

    private final PresenceRepository presenceRepository;
    private final PersonRepository personRepository;
    private final CourseRepository courseRepository;


    public PresenceService(PresenceRepository presenceRepository, PersonRepository personRepository, CourseRepository courseRepository) {
        this.presenceRepository = presenceRepository;
        this.personRepository = personRepository;
        this.courseRepository = courseRepository;
    }

    public Presence addPresence (AddPresenceDto newPresence){
        Person person = personRepository.getById(newPresence.personId());
        Course course = courseRepository.getById(newPresence.courseId());

        Presence presence = new Presence(person, newPresence.data(), course, newPresence.status());
        return presenceRepository.save(presence);
    }

    public Presence updatePresence (PresenceUpdateDto update){
        Presence presence = presenceRepository.findByDateAndPersonIdAndCourseId(update.date(), update.personId(), update.courseId())
                .orElseThrow(() -> new EntityNotFoundException("Presença não encontrada"));
        presence.setStatus(update.status());
        return presenceRepository.save(presence);
    }

    public List<Presence> getPresenceByPerson(Long id){
        return presenceRepository.findByPersonId(id);
    }

    public List<Presence> getPresenceByDateAndCourse(PresenceByDayAndCourseDto dayAndCourse){
        return presenceRepository.findByDateAndCourseId(dayAndCourse.date(), dayAndCourse.courseId());
    }
}
