package com.services;

import com.dtos.CourseDtos.AddCourseDto;
import com.dtos.CourseDtos.UpdateCourseDto;
import com.entities.Course;
import com.mappers.CourseMapper;
import com.repositories.CourseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import static com.utils.ValidationUtils.*;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseMapper mapper;

    public CourseService(CourseRepository courseRepository, CourseMapper mapper) {
        this.courseRepository = courseRepository;
        this.mapper = mapper;
    }

    public Course addCourse (AddCourseDto courseDto){
        required(courseDto.name(), "name");
        maxLength(courseDto.name(), 255, "name");
        maxLength(courseDto.description(), 255, "description");
        dateRange(courseDto.start(), courseDto.finish());
        return courseRepository.save(new Course(courseDto.name(), courseDto.description(),courseDto.start(), courseDto.finish()));
    }
    public Course updateCourse (UpdateCourseDto courseDto){
        positiveId(courseDto.courseId(), "courseId");
        Course course = courseRepository.findByid(courseDto.courseId())
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado"));
        if (courseDto.name() != null) {
            required(courseDto.name(), "name");
            maxLength(courseDto.name(), 255, "name");
        }
        maxLength(courseDto.description(), 255, "description");
        dateRange(courseDto.start() != null ? courseDto.start() : course.getStart(),
                courseDto.finish() != null ? courseDto.finish() : course.getFinish());
        mapper.updateCoursefromDto(courseDto, course);
        return courseRepository.save(course);
    }

    public void deleteCourse(Long courseId){
        positiveId(courseId, "id");
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado"));
        courseRepository.delete(course);
    }

    public List<Course> getAllCourses(){
        return courseRepository.findAll();
    }

    public Course getCourseByName(String courseName){
        required(courseName, "name");
        maxLength(courseName, 255, "name");
        return courseRepository.findByName(courseName)
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado"));
    }
}
