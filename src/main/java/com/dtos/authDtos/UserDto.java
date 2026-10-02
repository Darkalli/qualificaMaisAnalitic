package com.dtos.authDtos;

import com.entities.User;
import com.enums.UserRole;

public record UserDto(Long id, String username, UserRole role) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getRole());
    }
}
