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
    private final org.springframework.context.ApplicationEventPublisher events;

    @org.springframework.beans.factory.annotation.Autowired
    public CourseService(CourseRepository courseRepository, CourseMapper mapper,
                         org.springframework.context.ApplicationEventPublisher events) {
        this.courseRepository = courseRepository;
        this.mapper = mapper;
        this.events = events;
    }

    @org.springframework.transaction.annotation.Transactional
    public Course addCourse (AddCourseDto courseDto){
        required(courseDto.name(), "name");
        maxLength(courseDto.name(), 255, "name");
        maxLength(courseDto.description(), 255, "description");
        dateRange(courseDto.start(), courseDto.finish());
        Course saved = courseRepository.save(new Course(courseDto.name(), courseDto.description(),courseDto.start(), courseDto.finish()));
        events.publishEvent(new com.sheets.CourseCatalogChanged());
        return saved;
    }
    @org.springframework.transaction.annotation.Transactional
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
        Course saved = courseRepository.save(course);
        events.publishEvent(new com.sheets.CourseCatalogChanged());
        return saved;
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteCourse(Long courseId){
        positiveId(courseId, "id");
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado"));
        courseRepository.delete(course);
        events.publishEvent(new com.sheets.CourseCatalogChanged());
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
