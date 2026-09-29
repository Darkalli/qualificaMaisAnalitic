package com.dtos.courseClassesDtos;

import com.entities.Course;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AddCourseClassDto( LocalDate day, String session,
                                LocalDateTime start, LocalDateTime finish,
                                Course course) {
}
