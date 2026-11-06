package com.services;

import com.dtos.authDtos.AddUserDto;
import com.dtos.authDtos.UserDto;
import com.dtos.authDtos.UserUpdateDto;
import com.entities.User;
import com.repositories.AuthSessionRepository;
import com.repositories.UserRepository;
import com.security.AuthPrincipal;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class UserService implements UserDetailsService {
    private final UserRepository repository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, AuthSessionRepository authSessionRepository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.authSessionRepository = authSessionRepository;
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

    public List<UserDto> getAllUsers() {
        List<UserDto> dtos = new ArrayList<>();
        List<User> users = repository.findAll();
        for (User user : users) {
            UserDto dto = new UserDto(user.getId(), user.getUsername(), user.getRole());
            dtos.add(dto);
        }
        return dtos;
    }

    @Transactional
    public UserDto updateUser(UserUpdateDto dto) {
        User user = repository.findById(dto.id())
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
        user.setUsername(dto.username());
        user.setRole(dto.role());
        repository.save(user);
        return new UserDto(user.getId(), user.getUsername(), user.getRole());

    }

    @Transactional
    public void deleteUser(AuthPrincipal principal, Long id) {
        if (principal.userId().equals(id)) {
            throw new IllegalArgumentException("Não é possível excluir a própria conta");
        }
        if (repository.count() <= 1) {
            throw new IllegalArgumentException("Deve existir ao menos uma conta de usuário");
        }
        if (repository.existsById(id)) {
            authSessionRepository.deleteByUserId(id);
            repository.deleteById(id);
        } else {
            throw new EntityNotFoundException("Usuário não encontrado");
        }
    }

    public static String normalizeUsername(String username) {
        if (username == null || !username.strip().matches("[a-zA-Z0-9._-]{3,64}")) {
            throw new IllegalArgumentException("O usuário deve ter de 3 a 64 caracteres: letras, números, ponto, hífen ou sublinhado.");
        }
        return username.strip().toLowerCase(Locale.ROOT);
    }
}
