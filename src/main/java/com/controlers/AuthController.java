package com.controlers;

import com.dtos.CourseDtos.UpdateCourseDto;
import com.dtos.authDtos.*;
import com.entities.User;
import com.security.AuthPrincipal;
import com.services.AuthService;
import com.services.UserService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    public ResponseEntity<TokenDto> login(@RequestBody LoginDto dto) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.login(dto));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenDto> refresh(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authService.refresh(principal));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        authService.logout(principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@RequestBody AddUserDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.addUser(dto));
    }

    @PatchMapping
    public ResponseEntity<UserDto> update(@RequestBody UserUpdateDto dto){
        return ResponseEntity.ok().body(userService.updateUser(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id){
        userService.deleteUser(principal, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok().body(new UserDto(principal.userId(), principal.username(), principal.role()));
    }

    @GetMapping("/all")
    public ResponseEntity<List<UserDto>> getAll(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok().body(userService.getAllUsers());
    }
}
