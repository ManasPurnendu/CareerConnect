package com.careerconnect.auth.controller;

import com.careerconnect.auth.dto.AuthResponse;
import com.careerconnect.auth.dto.AuthUserDto;
import com.careerconnect.auth.dto.LoginRequest;
import com.careerconnect.auth.dto.RegisterRequest;
import com.careerconnect.auth.service.AuthService;
import com.careerconnect.common.response.ApiResponse;
import com.careerconnect.security.SecurityUser;
import com.careerconnect.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserDto>> getCurrentUser() {
        SecurityUser currentUser = SecurityUtils.getRequiredCurrentUser();
        AuthUserDto userDto = authService.getCurrentUser(currentUser);
        return ResponseEntity.ok(ApiResponse.success(userDto));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // In V1, tokens are stateless and held in frontend memory.
        // Returning 204 No Content confirms logout according to the Master Specification contract.
        return ResponseEntity.noContent().build();
    }
}
