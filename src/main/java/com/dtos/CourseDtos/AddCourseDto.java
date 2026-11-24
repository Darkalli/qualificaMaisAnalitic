package com.dtos.CourseDtos;

import com.enums.Period;

import java.time.LocalDate;

public record AddCourseDto(String name, String description,
                           LocalDate start, LocalDate finish, Period period, int maximumStudents) {
    public AddCourseDto(String name, String description, LocalDate start, LocalDate finish, int maximumStudents) {
        this(name, description, start, finish, null, maximumStudents);
    }
}
