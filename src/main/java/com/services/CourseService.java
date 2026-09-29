package com.services;

import com.dtos.CourseDtos.AddCourseDto;
import com.dtos.CourseDtos.UpdateCourseDto;
import com.entities.Course;
import com.entities.Person;
import com.mappers.CourseMapper;
import com.repositories.CourseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseMapper mapper;

    public CourseService(CourseRepository courseRepository, CourseMapper mapper) {
        this.courseRepository = courseRepository;
        this.mapper = mapper;
    }

    public void addCourse (AddCourseDto courseDto){
        courseRepository.save(new Course(courseDto.name(), courseDto.description(),courseDto.start(), courseDto.finish()));
    }
    public void updateCourse (Long courseId, UpdateCourseDto courseDto){
        Course course = courseRepository.findByid(courseId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        mapper.updateCoursefromDto(courseDto, course);
        courseRepository.save(course);
    }

    public void deleteCourse(Long courseId){
        courseRepository.delete(courseRepository.getById(courseId));
    }

    public List<Course> getAllCourses(){
        return courseRepository.findAll();
    }

    public Optional<Course> getCourseByName(String courseName){
        return courseRepository.findByName(courseName);
    }
}
