package com.services;

import com.dtos.authDtos.*;
import com.entities.AuthSession;
import com.entities.User;
import com.repositories.AuthSessionRepository;
import com.repositories.UserRepository;
import com.security.AuthPrincipal;
import org.springframework.security.authentication.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {
    private static final Duration TOKEN_DURATION = Duration.ofDays(15);
    private final SecureRandom random = new SecureRandom();
    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final AuthenticationManager authenticationManager;
    private final Clock clock;

    public AuthService(AuthSessionRepository sessions, UserRepository users,
                       AuthenticationManager authenticationManager, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.authenticationManager = authenticationManager;
        this.clock = clock;
    }

    @Transactional
    public TokenDto login(LoginDto dto) {
        String username = UserService.normalizeUsername(dto.username());
        if (dto.password() == null || dto.password().isBlank()) {
            throw new IllegalArgumentException("A senha é obrigatória.");
        }
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, dto.password()));
        User user = users.findByUsername(username).orElseThrow(() -> new BadCredentialsException("Login inválido."));
        return issueToken(new AuthSession(user, null, null));
    }

    @Transactional(readOnly = true)
    public AuthPrincipal authenticateToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidToken();
        }
        AuthSession session = sessions.findByTokenHash(hashToken(token)).orElseThrow(this::invalidToken);
        if (!session.getExpiresAt().isAfter(clock.instant())) {
            throw invalidToken();
        }
        User user = session.getUser();
        return new AuthPrincipal(user.getId(), user.getUsername(), user.getRole(), session.getId(), session.getTokenHash());
    }

    @Transactional
    public TokenDto refresh(AuthPrincipal principal) {
        return issueToken(lockSession(principal));
    }

    @Transactional
    public void logout(AuthPrincipal principal) {
        sessions.delete(lockSession(principal));
    }

    private AuthSession lockSession(AuthPrincipal principal) {
        AuthSession session = sessions.findByIdForUpdate(principal.sessionId()).orElseThrow(this::invalidToken);
        // Revalidar após o lock impede duas renovações usando o mesmo token antigo.
        if (!session.getTokenHash().equals(principal.tokenHash()) || !session.getExpiresAt().isAfter(clock.instant())) {
            throw invalidToken();
        }
        return session;
    }

    private TokenDto issueToken(AuthSession session) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setTokenHash(hashToken(token));
        session.setExpiresAt(clock.instant().plus(TOKEN_DURATION));
        sessions.saveAndFlush(session);
        return new TokenDto(token, "Bearer", session.getExpiresAt(), UserDto.from(session.getUser()));
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
    }

    private BadCredentialsException invalidToken() {
        return new BadCredentialsException("Token inválido ou expirado. Entre novamente.");
    }
}
