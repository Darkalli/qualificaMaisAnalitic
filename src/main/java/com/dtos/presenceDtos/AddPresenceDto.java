package com.dtos.presenceDtos;

import com.enums.PresenceStatus;

import java.time.LocalDate;

public record AddPresenceDto(Long personId, LocalDate data, Long courseId, PresenceStatus status) {
}
