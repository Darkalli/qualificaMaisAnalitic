package com.dtos.presenceDtos;

import com.enums.PresenceStatus;

public record AddPresenceDto(Long personId, Long courseClassId, PresenceStatus status) {
}
