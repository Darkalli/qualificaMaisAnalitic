package com.dtos.courseClassesDtos;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;

public record AddCourseClassInBatchDto(ArrayList<LocalDate> day, String session,
                                       LocalTime start, LocalTime finish,
                                       Long courseId) {
}
