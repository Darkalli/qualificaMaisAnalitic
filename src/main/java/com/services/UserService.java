package com.services;

import com.dtos.authDtos.AddUserDto;
import com.dtos.authDtos.UserDto;
import com.entities.User;
import com.repositories.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class UserService implements UserDetailsService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserDto addUser(AddUserDto dto) {
        String username = normalizeUsername(dto.username());
        if (dto.password() == null || dto.password().isBlank() || dto.password().length() < 8
                || dto.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres e no máximo 72 bytes em UTF-8.");
        }
        if (dto.role() == null) {
            throw new IllegalArgumentException("O perfil do usuário é obrigatório.");
        }
        User user = new User(username, passwordEncoder.encode(dto.password()), dto.role());
        return UserDto.from(repository.saveAndFlush(user));
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário ou senha inválidos."));
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPasswordHash()).roles(user.getRole().name()).build();
    }

    public static String normalizeUsername(String username) {
        if (username == null || !username.strip().matches("[a-zA-Z0-9._-]{3,64}")) {
            throw new IllegalArgumentException("O usuário deve ter de 3 a 64 caracteres: letras, números, ponto, hífen ou sublinhado.");
        }
        return username.strip().toLowerCase(Locale.ROOT);
    }
}
