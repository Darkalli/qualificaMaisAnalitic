package com.services;

import com.dtos.CourseDtos.CourseDto;
import com.dtos.CourseDtos.UpdateCourseDto;
import com.entities.Course;
import com.enums.StatusCourse;
import com.mappers.CourseMapper;
import com.repositories.CourseRepository;
import com.sheets.CourseCatalogChanged;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.checkerframework.checker.units.qual.C;
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

    @Transactional
    public CourseDto addCourse(CourseDto courseDto) {
        required(courseDto.name(), "name");
        maxLength(courseDto.name(), 255, "name");
        maxLength(courseDto.description(), 255, "description");
        dateRange(courseDto.start(), courseDto.finish());
        positiveInt(courseDto.maximumStudents(), "maximumStudents");
        Course course = new Course(courseDto.name(), courseDto.description(), courseDto.start(), courseDto.finish(), StatusCourse.ACTIVE, courseDto.maximumStudents());
        course.setPeriod(courseDto.period());
        Course saved = courseRepository.save(course);
        events.publishEvent(new CourseCatalogChanged());
        return new CourseDto(saved);
    }

    @Transactional
    public CourseDto updateCourse(UpdateCourseDto courseDto) {
        positiveId(courseDto.courseId(), "courseId");
        Course course = courseRepository.findByid(courseDto.courseId())
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado"));
        if (courseDto.name() != null) {
            required(courseDto.name(), "name");
            maxLength(courseDto.name(), 255, "name");
        }
        maxLength(courseDto.description(), 255, "description");
        positiveInt(courseDto.maximumStudents(), "maximumStudents");
        dateRange(courseDto.start() != null ? courseDto.start() : course.getStart(),
                courseDto.finish() != null ? courseDto.finish() : course.getFinish());
        mapper.updateCoursefromDto(courseDto, course);
        courseRepository.save(course);
        events.publishEvent(new CourseCatalogChanged());
        return new CourseDto(course);
    }

    @Transactional
    public CourseDto updateCourseStatus(Long courseId) {
        Course course = courseRepository.findByid(courseId)
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado"));
        if (course.getStatusCourse() == StatusCourse.ACTIVE) {
            course.setStatusCourse(StatusCourse.CLOSED);
        } else {
            course.setStatusCourse(StatusCourse.ACTIVE);
        }
        courseRepository.save(course);
        return new CourseDto(course);
    }

    @Transactional
    public void deleteCourse(Long courseId) {
        positiveId(courseId, "id");
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado"));
        course.setStatusCourse(StatusCourse.CANCELED);
        events.publishEvent(new CourseCatalogChanged());
    }

    public List<CourseDto> getAllCourses() {
        return courseRepository.findAll().stream()
                .map(CourseDto::new)
                .toList();

    }

    public CourseDto getCourseByName(String courseName) {
        required(courseName, "name");
        maxLength(courseName, 255, "name");
        return courseRepository.findByName(courseName)
                .orElseThrow(() -> new EntityNotFoundException("Curso não encontrado"));
    }
}
