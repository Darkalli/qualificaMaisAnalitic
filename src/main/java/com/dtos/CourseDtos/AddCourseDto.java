package com.dtos.CourseDtos;

import com.enums.Period;

import java.time.LocalDate;

public record AddCourseDto(String name, String description,
                           LocalDate start, LocalDate finish, Period period) {
    public AddCourseDto(String name, String description, LocalDate start, LocalDate finish) {
        this(name, description, start, finish, null);
    }
}
