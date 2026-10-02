package com.example.qualificamaisanalitic.controllers;

import com.dtos.authDtos.*;
import com.enums.UserRole;
import com.jayway.jsonpath.JsonPath;
import com.repositories.AuthSessionRepository;
import com.repositories.UserRepository;
import com.services.AuthService;
import com.services.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(AuthControllerTests.TimeConfiguration.class)
class AuthControllerTests {
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final String PASSWORD = "Example-pass-2026";
    @Autowired private WebApplicationContext context;
    @Autowired private UserService userService;
    @Autowired private UserRepository users;
    @Autowired private AuthSessionRepository sessions;
    @Autowired private AuthService authService;
    @Autowired private PasswordEncoder encoder;
    @Autowired private TestClock clock;
    private MockMvc mvc;

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary
        TestClock testClock() { return new TestClock(); }
    }

    static class TestClock extends Clock {
        private volatile Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
        void at(Instant value) { now = value; }
    }

    @BeforeEach
    void setUp() {
        clean();
        clock.at(NOW);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void clean() {
        sessions.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void loginStoresOnlyHashesAndAuthenticatesWithoutHttpSession() throws Exception {
        userService.addUser(new AddUserDto(" Agente ", PASSWORD, UserRole.AGENT));
        var response = login("AGENTE", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("agente"))
                .andExpect(jsonPath("$.user.role").value("AGENT"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value(NOW.plus(Duration.ofDays(15)).toString()))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
        String token = token(response);
        var stored = users.findByUsername("agente").orElseThrow();
        assertNotEquals(PASSWORD, stored.getPasswordHash());
        assertTrue(encoder.matches(PASSWORD, stored.getPasswordHash()));
        assertNotEquals(token, sessions.findAll().getFirst().getTokenHash());
        assertEquals(64, sessions.findAll().getFirst().getTokenHash().length());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("agente"))
                .andExpect(jsonPath("$.tokenHash").doesNotExist());
        mvc.perform(get("/api/course").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void badPasswordAndUnknownUsernameReturnTheSameUnauthorizedResponse() throws Exception {
        userService.addUser(new AddUserDto("agente", PASSWORD, UserRole.AGENT));
        String first = login("agente", "incorrect-password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401)).andReturn().getResponse().getContentAsString();
        String second = login("unknown", PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertEquals(first, second);
        assertEquals(0, sessions.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/person", "/api/course", "/api/courseClass/courseClass/1", "/api/presence/presence/1", "/api/register/register/01234567890", "/api/auth/me"})
    void businessRoutesRequireAuthentication(String route) throws Exception {
        mvc.perform(get(route)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401)).andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void registrationRefreshAndLogoutAreNotPublicEvenWhenDatabaseHasNoUsers() throws Exception {
        for (String route : new String[]{"register", "refresh", "logout"}) {
            mvc.perform(post("/api/auth/" + route).contentType(APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        assertEquals(0, users.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer invalid", "Basic dXNlcjpwYXNz", "Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void malformedOrForgedTokensCannotAuthenticate(String authorization) throws Exception {
        mvc.perform(get("/api/course").header("Authorization", authorization))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void startupRefreshRotatesTokenAndExtendsLoginForAnotherFifteenDays() throws Exception {
        String old = createLogin(UserRole.AGENT);
        clock.at(NOW.plus(Duration.ofDays(14)));
        String renewed = token(mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + old))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value(NOW.plus(Duration.ofDays(29)).toString())));
        assertNotEquals(old, renewed);
        assertEquals(1, sessions.count());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + old)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + old)).andExpect(status().isUnauthorized());
        clock.at(NOW.plus(Duration.ofDays(16)));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + renewed)).andExpect(status().isOk());
        clock.at(NOW.plus(Duration.ofDays(29)));
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + renewed)).andExpect(status().isUnauthorized());
    }

    @Test
    void normalRequestsDoNotSilentlyExtendExpirationAndExpiredTokenNeedsPassword() throws Exception {
        String token = createLogin(UserRole.AGENT);
        clock.at(NOW.plus(Duration.ofDays(14)));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertEquals(NOW.plus(Duration.ofDays(15)), sessions.findAll().getFirst().getExpiresAt());
        clock.at(NOW.plus(Duration.ofDays(15)));
        mvc.perform(get("/api/course").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        login("operator", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void logoutRevokesOnlyTheCurrentComputerSession() throws Exception {
        String first = createLogin(UserRole.AGENT);
        String second = token(login("operator", PASSWORD).andExpect(status().isOk()));
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + first)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + second)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void bothRolesCanUseTheSameRoutesAndRegisterEitherRole(UserRole role) throws Exception {
        String token = createLogin(role);
        mvc.perform(get("/api/course").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        for (UserRole newRole : UserRole.values()) {
            mvc.perform(post("/api/auth/register").header("Authorization", "Bearer " + token)
                            .contentType(APPLICATION_JSON).content("""
                            {"username":"new-%s","password":"Example-pass-2026","role":"%s"}
                            """.formatted(newRole.name().toLowerCase(), newRole.name())))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value(newRole.name()))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }
        mvc.perform(post("/api/auth/register").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("""
                        {"username":" OPERATOR ","password":"Example-pass-2026","role":"ADMIN"}
                        """))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"username\":\"newuser\",\"password\":\"short\",\"role\":\"AGENT\"}",
            "{\"username\":\"newuser\",\"password\":\"Example-pass-2026\",\"role\":\"OTHER\"}"})
    void invalidRegistrationIsRejectedWithoutSaving(String body) throws Exception {
        String token = createLogin(UserRole.ADMIN);
        mvc.perform(post("/api/auth/register").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(1, users.count());
    }

    @Test
    void simultaneousRefreshAllowsOnlyOneRotation() throws Exception {
        String token = createLogin(UserRole.AGENT);
        var principal = authService.authenticateToken(token);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<TokenDto> call = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try { return authService.refresh(principal); }
                catch (BadCredentialsException expected) { return null; }
            };
            var first = executor.submit(call);
            var second = executor.submit(call);
            start.countDown();
            var one = first.get(10, TimeUnit.SECONDS);
            var two = second.get(10, TimeUnit.SECONDS);
            assertTrue((one == null) != (two == null));
            var winner = one != null ? one : two;
            assertNotNull(authService.authenticateToken(winner.token()));
            assertThrows(BadCredentialsException.class, () -> authService.authenticateToken(token));
            assertEquals(1, sessions.count());
        }
    }

    @Test
    void swaggerRemainsPublicAndOffersBearerAuthorization() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    private String createLogin(UserRole role) throws Exception {
        userService.addUser(new AddUserDto("operator", PASSWORD, role));
        return token(login("operator", PASSWORD).andExpect(status().isOk()));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private String token(ResultActions response) throws Exception {
        return JsonPath.read(response.andReturn().getResponse().getContentAsString(), "$.token");
    }
}
