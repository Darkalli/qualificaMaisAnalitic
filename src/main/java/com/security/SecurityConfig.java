package com.security;

import com.services.AuthService;
import com.services.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import java.time.Clock;

@Configuration
@EnableMethodSecurity
@io.swagger.v3.oas.annotations.security.SecurityScheme(name = "bearerAuth",
        type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP, scheme = "bearer")
@io.swagger.v3.oas.annotations.OpenAPIDefinition(security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth"))
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService authService) throws Exception {
        var errors = new SecurityErrorHandler();
        return http
                .csrf(csrf -> csrf.disable()) // Autenticação apenas pelo header Bearer, sem cookies.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // ADMIN e AGENT têm as mesmas permissões. Restrições futuras ficam aqui ou em @PreAuthorize.
                        .anyRequest().authenticated())
                .addFilterBefore(new TokenAuthenticationFilter(authService, errors), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
