package com.security;

import com.enums.UserRole;

public record AuthPrincipal(Long userId, String username, UserRole role, Long sessionId, String tokenHash) {
}
