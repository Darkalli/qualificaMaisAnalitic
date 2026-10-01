package com.services;

import com.dtos.courseClassesDtos.AddCourseClassDto;
import com.dtos.courseClassesDtos.UpdateCourseClassDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.mappers.CourseClassMapper;
import com.repositories.CourseClassRepository;
import com.repositories.CourseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CourseClassService {

    private final CourseClassRepository courseClassRepository;
    private final CourseRepository courseRepository;
    private final CourseClassMapper mapper;

    public CourseClassService(CourseClassRepository courseClassRepository, CourseRepository courseRepository, CourseClassMapper mapper) {
        this.courseClassRepository = courseClassRepository;
        this.courseRepository = courseRepository;
        this.mapper = mapper;
    }

    public CourseClass addCourseClass(AddCourseClassDto dto) {
        if (courseClassRepository.existsByCourseAndDay(dto.course(), dto.day())) {
            throw new IllegalArgumentException("O curso já possui uma aula registrada para este dia.");
        }
        return courseClassRepository.save(new CourseClass(dto.day(), dto.session(), dto.start(), dto.finish(), dto.course()));
    }

    public CourseClass updateCourseClass(UpdateCourseClassDto dto){
        CourseClass courseClass = courseClassRepository.findById(dto.classId())
                .orElseThrow(() -> new EntityNotFoundException("Turma não encontrada com o ID: " + dto.classId()));
        Course course = dto.course() != null ? dto.course() : courseClass.getCourse();
        LocalDate day = dto.day() != null ? dto.day() : courseClass.getDay();
        if (courseClassRepository.existsByCourseAndDayAndIdNot(course, day, dto.classId())) {
            throw new IllegalArgumentException("O curso já possui uma aula registrada para este dia.");
        }
        mapper.updateCourseClassfromDto(dto,courseClass);
        return courseClassRepository.save(courseClass);
    }

    public void deleteCourseClass(Long id){
        courseClassRepository.delete(courseClassRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Turma não encontrada com o ID: " + id)));
    }

    public List<CourseClass> allClassesByCourseId(Long courseId){
        Course course = courseRepository.findByid(courseId)
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado com o ID: " + courseId));
        return courseClassRepository.findByCourse(course);
    }
}
