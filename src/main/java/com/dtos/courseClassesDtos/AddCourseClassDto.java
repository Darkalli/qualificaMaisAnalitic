package com.dtos.courseClassesDtos;

import com.enums.Period;
import com.enums.StatusClass;

import java.time.LocalDate;
import java.time.LocalTime;

public record AddCourseClassDto( LocalDate day, Period session,
                                LocalTime start, LocalTime finish,
                                Long courseId, StatusClass statusClass) {
    public AddCourseClassDto(LocalDate day, Period session, LocalTime start, LocalTime finish, Long courseId) {
        this(day, session, start, finish, courseId, null);
    }
}
