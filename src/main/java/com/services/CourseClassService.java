package com.services;

import com.dtos.courseClassesDtos.AddCourseClassDto;
import com.dtos.courseClassesDtos.UpdateCourseClassDto;
import com.entities.Course;
import com.entities.CourseClass;
import com.repositories.CourseClassRepository;
import com.repositories.CourseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseClassService {

    private final CourseClassRepository courseClassRepository;
    private final CourseRepository courseRepository;

    public CourseClassService(CourseClassRepository courseClassRepository, CourseRepository courseRepository) {
        this.courseClassRepository = courseClassRepository;
        this.courseRepository = courseRepository;
    }

    public void addCourseClass(AddCourseClassDto courseClassDto){
        courseClassRepository.save(new CourseClass(courseClassDto.day(), courseClassDto.session(),
                courseClassDto.start(), courseClassDto.finish(), courseClassDto.course()));
    }

    public void updateCourseClass(UpdateCourseClassDto updateCourseClassDto){
        CourseClass courseClass = courseClassRepository.findById(updateCourseClassDto.classId())
                .orElseThrow(() -> new EntityNotFoundException("Turma não encontrada com o ID: " + updateCourseClassDto.classId()));
        courseClassRepository.save(courseClass);
    }

    public void deleteCourseClass(Long id){
        courseClassRepository.delete(courseClassRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Turma não encontrada com o ID: " + id)));
    }

    public List<CourseClass> allClassesByCourse(Long courseId){
        Course course = courseRepository.findByid(courseId)
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado com o ID: " + courseId));
        return courseClassRepository.findByCourse(course);
    }
}
