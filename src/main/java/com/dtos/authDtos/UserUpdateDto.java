package com.dtos.authDtos;

import com.enums.UserRole;

public record UserUpdateDto(Long id, String username, UserRole role) {
}
