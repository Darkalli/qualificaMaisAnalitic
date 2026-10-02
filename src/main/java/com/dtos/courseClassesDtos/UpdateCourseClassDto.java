package com.dtos.courseClassesDtos;

import com.entities.Course;
import com.enums.StatusClass;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateCourseClassDto(Long classId, @JsonProperty(required = false) LocalDate day, @JsonProperty(required = false) String session,
                                   @JsonProperty(required = false) LocalTime start, @JsonProperty(required = false) LocalTime finish,
                                   @JsonProperty(required = false) Course course,
                                   @JsonProperty(required = false) StatusClass statusClass) {
    public UpdateCourseClassDto(Long classId, LocalDate day, String session, LocalTime start, LocalTime finish, Course course) {
        this(classId, day, session, start, finish, course, null);
    }
}
