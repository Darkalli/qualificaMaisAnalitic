package com.dtos.courseClassesDtos;

import com.entities.Course;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UpdateCourseClassDto(Long classId, @JsonProperty(required = false) LocalDate day, @JsonProperty(required = false) String session,
                                   @JsonProperty(required = false) LocalDateTime start, @JsonProperty(required = false) LocalDateTime finish,
                                   @JsonProperty(required = false) Course course) {
}
