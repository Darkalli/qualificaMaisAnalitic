package com.dtos.presenceDtos;

import com.enums.PresenceStatus;

import java.time.LocalDate;

public record PresenceUpdateDto(Long personId, LocalDate date, PresenceStatus status, Long courseId) {
}
