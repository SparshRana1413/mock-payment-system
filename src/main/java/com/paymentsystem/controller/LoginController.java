package com.paymentsystem.controller;

import com.paymentsystem.dto.LoginRequest;
import com.paymentsystem.model.User;
import com.paymentsystem.service.LoginService;
import com.paymentsystem.service.JwtService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final LoginService loginService;
    private final JwtService jwtService;

    public LoginController(
            LoginService loginService,
            JwtService jwtService) {

        this.loginService = loginService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request) {

        User user = loginService.login(request);

        String token = jwtService.generateToken(
            user.getId().toString()
        );

        ResponseCookie cookie = ResponseCookie
            .from("access_token", token)
            .httpOnly(true)
            .secure(false) // Local HTTP development only
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofMinutes(15))
            .build();

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(
                Map.of(
                    "message", "Login successful",
                    "userId", user.getId(),
                    "username", user.getUsername(),
                    "email", user.getEmail()
                )
            );
    }
}
