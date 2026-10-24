package com.paymentsystem.controller;

import com.paymentsystem.dto.SignupRequest;
import com.paymentsystem.model.User;
import com.paymentsystem.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record SignupResponse(
        Long id,
        String username,
        String email
    ) {}

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request) {

        User user = authService.signup(request);

        SignupResponse response = new SignupResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
