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

    public CourseClass addCourseClass(AddCourseClassDto courseClassDto){
        return courseClassRepository.save(new CourseClass(courseClassDto.day(), courseClassDto.session(),
                courseClassDto.start(), courseClassDto.finish(), courseClassDto.course()));
    }

    public CourseClass updateCourseClass(UpdateCourseClassDto updateCourseClassDto){
        CourseClass courseClass = courseClassRepository.findById(updateCourseClassDto.classId())
                .orElseThrow(() -> new EntityNotFoundException("Turma não encontrada com o ID: " + updateCourseClassDto.classId()));
        mapper.updateCourseClassfromDto(updateCourseClassDto,courseClass);
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
