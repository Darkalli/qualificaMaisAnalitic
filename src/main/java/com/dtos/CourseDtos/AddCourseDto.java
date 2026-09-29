package com.dtos.CourseDtos;

import com.entities.CourseClass;

import java.time.LocalDate;
import java.util.ArrayList;

public record AddCourseDto(String name, String description,
                           LocalDate start, LocalDate finish) {
}
