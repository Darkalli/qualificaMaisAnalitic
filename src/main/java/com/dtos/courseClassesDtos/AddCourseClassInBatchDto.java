package com.dtos.courseClassesDtos;

import com.enums.Period;
import com.enums.StatusClass;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;

public record AddCourseClassInBatchDto(ArrayList<LocalDate> day, Period session,
                                       LocalTime start, LocalTime finish,
                                       Long courseId, StatusClass statusClass) {
    public AddCourseClassInBatchDto(ArrayList<LocalDate> day, Period session, LocalTime start, LocalTime finish, Long courseId) {
        this(day, session, start, finish, courseId, null);
    }
}
