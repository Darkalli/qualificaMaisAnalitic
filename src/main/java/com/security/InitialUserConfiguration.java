package com.security;

import com.dtos.authDtos.AddUserDto;
import com.enums.UserRole;
import com.repositories.UserRepository;
import com.services.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InitialUserConfiguration {
    @Bean
    public ApplicationRunner initialUser(UserRepository users, UserService service,
            @Value("${app.auth.bootstrap.username:${AUTH_BOOTSTRAP_USERNAME:}}") String username,
            @Value("${app.auth.bootstrap.password:${AUTH_BOOTSTRAP_PASSWORD:}}") String password) {
        return args -> {
            if (users.count() > 0 || (username.isBlank() && password.isBlank())) {
                return;
            }
            if (username.isBlank() || password.isBlank()) {
                throw new IllegalStateException("Informe usuário e senha para criar o primeiro usuário de acesso.");
            }
            service.addUser(new AddUserDto(username, password, UserRole.ADMIN));
        };
    }
}
