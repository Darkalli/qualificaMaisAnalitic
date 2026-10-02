package com.dtos.authDtos;

import com.enums.UserRole;

public record AddUserDto(String username, String password, UserRole role) {
}
