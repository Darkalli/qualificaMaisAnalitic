package com.dtos.courseClassesDtos;

import com.entities.Course;

import java.time.LocalDate;
import java.time.LocalTime;

public record AddCourseClassDto( LocalDate day, String session,
                                LocalTime start, LocalTime finish,
                                Course course) {
}
