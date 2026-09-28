package com.dtos.presenceDtos;

import java.time.LocalDate;

public record PresenceByDayAndCourseDto(Long courseId, LocalDate date) {
}
