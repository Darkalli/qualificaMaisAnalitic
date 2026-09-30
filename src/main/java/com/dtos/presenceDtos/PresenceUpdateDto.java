package com.dtos.presenceDtos;

import com.enums.PresenceStatus;

public record PresenceUpdateDto(Long personId, Long courseClassId, PresenceStatus status) {
}
