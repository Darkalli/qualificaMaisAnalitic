package com.services;

import com.dtos.CourseDtos.AddCourseDto;
import com.dtos.CourseDtos.UpdateCourseDto;
import com.entities.Course;
import com.mappers.CourseMapper;
import com.repositories.CourseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseMapper mapper;

    public CourseService(CourseRepository courseRepository, CourseMapper mapper) {
        this.courseRepository = courseRepository;
        this.mapper = mapper;
    }

    public Course addCourse (AddCourseDto courseDto){
        return courseRepository.save(new Course(courseDto.name(), courseDto.description(),courseDto.start(), courseDto.finish()));
    }
    public Course updateCourse (UpdateCourseDto courseDto){
        Course course = courseRepository.findByid(courseDto.courseId())
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado"));
        mapper.updateCoursefromDto(courseDto, course);
        return courseRepository.save(course);
    }

    public void deleteCourse(Long courseId){
        courseRepository.delete(courseRepository.getById(courseId));
    }

    public List<Course> getAllCourses(){
        return courseRepository.findAll();
    }

    public Course getCourseByName(String courseName){
        return courseRepository.findByName(courseName)
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado"));
    }
}
