package com.example.qualificamaisanalitic.services;

import com.dtos.authDtos.AddUserDto;
import com.enums.UserRole;
import com.repositories.UserRepository;
import com.security.InitialUserConfiguration;
import com.services.UserService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InitialUserConfigurationTests {
    private final UserRepository users = mock(UserRepository.class);
    private final UserService service = mock(UserService.class);
    private final InitialUserConfiguration configuration = new InitialUserConfiguration();

    @Test
    void createsInitialUserOnlyWhenCredentialsWereExplicitlyConfigured() throws Exception {
        configuration.initialUser(users, service, "", "").run(null);
        verifyNoInteractions(service);
        configuration.initialUser(users, service, "operator", "Example-pass-2026").run(null);
        verify(service).addUser(new AddUserDto("operator", "Example-pass-2026", UserRole.ADMIN));
    }

    @Test
    void startupNeverResetsExistingUsersOrTheirPasswords() throws Exception {
        when(users.count()).thenReturn(1L);
        configuration.initialUser(users, service, "another-user", "another-password").run(null);
        verifyNoInteractions(service);
    }

    @Test
    void partialConfigurationFailsWithoutCreatingAnAccount() {
        assertThrows(IllegalStateException.class, () -> configuration.initialUser(users, service, "operator", "").run(null));
        assertThrows(IllegalStateException.class, () -> configuration.initialUser(users, service, "", "password").run(null));
        verifyNoInteractions(service);
    }
}
