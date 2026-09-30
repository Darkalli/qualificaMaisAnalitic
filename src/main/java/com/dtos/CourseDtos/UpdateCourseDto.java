package com.dtos.CourseDtos;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record UpdateCourseDto(Long courseId, @JsonProperty(required = false) String name, @JsonProperty(required = false) String description,
                              @JsonProperty(required = false) LocalDate start, @JsonProperty(required = false) LocalDate finish) {
}
