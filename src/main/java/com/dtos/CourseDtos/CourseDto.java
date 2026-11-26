package com.dtos.CourseDtos;

import com.entities.Course;
import com.enums.Period;
import com.enums.StatusCourse;

import java.time.LocalDate;

public record CourseDto(Long id, String name, String description,
                        LocalDate start, LocalDate finish, Period period, StatusCourse statusCourse, int maximumStudents) {
    public CourseDto(String name, String description, LocalDate start, LocalDate finish, Period period, StatusCourse statusCourse, int maximumStudents) {
        this(null, name, description, start, finish, period, statusCourse, maximumStudents);
    }

    public CourseDto(String name, String description, LocalDate start, LocalDate finish, Period period, int maximumStudents) {
        this(null, name, description, start, finish, period, StatusCourse.ACTIVE, maximumStudents);
    }

    public CourseDto(String name, String description, LocalDate start, LocalDate finish, int maximumStudents) {
        this(name, description, start, finish, null, maximumStudents);
    }

    public CourseDto(Long id, String name, String description, LocalDate start, LocalDate finish, Period period, int maximumStudents) {
        this(id, name, description, start, finish, period, StatusCourse.ACTIVE, maximumStudents);
    }

    public CourseDto(Course course) {
        this(course.getId(), course.getName(), course.getDescription(),
                course.getStart(), course.getFinish(),
                course.getPeriod(), course.getStatusCourse(), course.getMaximumStudentsCount());
    }
}
