package com.dtos.authDtos;

import java.time.Instant;

public record TokenDto(String token, String tokenType, Instant expiresAt, UserDto user) {
}
