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

/**
 * REST controller handling user login and session cookie management.
 *
 * <p><strong>Architectural Role:</strong>
 * Presentation/Web layer. Orchestrates authentication by delegating credential checks
 * to {@link LoginService}, issuing a signed JWT via {@link JwtService}, and attaching it to
 * the HTTP response as an HttpOnly cookie per ADR-003.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to an Express login route handler (e.g. {@code router.post('/login', ...)})
 * that verifies credentials, signs a JWT via {@code jsonwebtoken.sign()}, and calls
 * {@code res.cookie('access_token', token, { httpOnly: true, sameSite: 'lax' }).json(...)}.</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Receives {@link LoginRequest}, calls {@link LoginService#login(LoginRequest)} to authenticate,
 * invokes {@link JwtService#generateToken(String)}, and constructs a {@link ResponseCookie}.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Password verification belongs in {@link LoginService}; cryptographic token signing belongs
 * in {@link JwtService}.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final LoginService loginService;
    private final JwtService jwtService;

    /**
     * Constructs the controller with required authentication and token services.
     *
     * @param loginService the service handling credential verification
     * @param jwtService the service responsible for creating signed JWT strings
     */
    public LoginController(
            LoginService loginService,
            JwtService jwtService) {

        this.loginService = loginService;
        this.jwtService = jwtService;
    }

    /**
     * Authenticates a user and sets an HttpOnly JWT cookie upon success.
     *
     * <p>On successful credential verification, an {@code access_token} cookie with a 15-minute
     * TTL is added to the {@code Set-Cookie} header, and a sanitized user profile is returned in the body.</p>
     *
     * @param request the login credentials payload containing email and password
     * @return a {@link ResponseEntity} with an HTTP 200 OK status, Set-Cookie header, and user metadata
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request) {

        // Validate credentials against PostgreSQL via LoginService
        User user = loginService.login(request);

        // Generate a signed JWT with user ID as subject
        String token = jwtService.generateToken(
            user.getId().toString()
        );

        // Deliver JWT strictly via HttpOnly cookie (ADR-003) to prevent XSS access
        ResponseCookie cookie = ResponseCookie
            .from("access_token", token)
            .httpOnly(true) // Blocks client-side document.cookie access
            .secure(false) // Local HTTP development only; set to true in production with HTTPS
            .sameSite("Lax") // Helps mitigate Cross-Site Request Forgery (CSRF)
            .path("/")
            .maxAge(Duration.ofMinutes(15)) // 15-minute lifespan aligned with JWT expiration
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
