package com.paymentsystem.controller;

import com.paymentsystem.dto.LoginRequest;
import com.paymentsystem.model.User;
import com.paymentsystem.service.JwtService;
import com.paymentsystem.service.LoginService;
import com.paymentsystem.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * REST controller handling user login, token refresh, session cookie management, and logout.
 *
 * <p><strong>Architectural Role:</strong>
 * Presentation/Web layer. Coordinates credential verification, dual-token issuance
 * (short-lived JWT access token and persisted rotating refresh token), cookie lifecycle management,
 * and session revocation per ADR-003.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class LoginController {

    public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    private final LoginService loginService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final boolean cookieSecure;

    /**
     * Response payload representing sanitized authenticated user details.
     *
     * @param message human-readable status message
     * @param userId unique primary key of the authenticated user
     * @param username chosen username
     * @param email registered email address
     * @param role assigned authorization role
     */
    public record LoginResponse(
        String message,
        Long userId,
        String username,
        String email,
        String role
    ) {}

    /**
     * Constructs the controller with required authentication, JWT, and refresh token services.
     *
     * @param loginService the service handling credential verification
     * @param jwtService the service responsible for creating signed JWT strings
     * @param refreshTokenService service managing refresh token persistence and rotation
     * @param cookieSecure whether authentication cookies should enforce the {@code Secure} HTTPS flag
     */
    public LoginController(
            LoginService loginService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            @Value("${jwt.cookie.secure:false}") boolean cookieSecure) {

        this.loginService = loginService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.cookieSecure = cookieSecure;
    }

    /**
     * Authenticates a user and sets both {@code access_token} and {@code refresh_token} cookies.
     *
     * @param request the validated login credentials payload containing email and password
     * @return a {@link ResponseEntity} with HTTP 200 OK, Set-Cookie headers, and user metadata
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        // Validate credentials against PostgreSQL via LoginService
        User user = loginService.login(request);

        // Generate signed JWT access token containing user ID and role
        String accessToken = jwtService.generateToken(
                user.getId().toString(),
                List.of(user.getRole().toAuthority())
        );

        // Issue and persist a new refresh token session
        RefreshTokenService.IssuedToken issuedToken = refreshTokenService.createRefreshToken(user);

        ResponseCookie accessCookie = buildAccessCookie(accessToken, Duration.ofMinutes(15));
        ResponseCookie refreshCookie = buildRefreshCookie(issuedToken.rawToken(), Duration.ofDays(7));

        LoginResponse response = new LoginResponse(
            "Login successful",
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole().name()
        );

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
            .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
            .body(response);
    }

    /**
     * Renews access and refresh tokens using a valid {@code refresh_token} cookie.
     *
     * <p>Enforces refresh token rotation. If reuse is detected, revokes the token family.</p>
     *
     * @param request the incoming servlet request
     * @return HTTP 200 OK with new cookies and confirmation
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(HttpServletRequest request) {
        String rawRefreshToken = extractCookie(request, REFRESH_TOKEN_COOKIE_NAME);
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException("Refresh token is missing");
        }

        RefreshTokenService.RefreshResult result = refreshTokenService.rotateRefreshToken(rawRefreshToken);
        User user = result.user();

        // Issue new short-lived access JWT
        String newAccessToken = jwtService.generateToken(
                user.getId().toString(),
                List.of(user.getRole().toAuthority())
        );

        ResponseCookie accessCookie = buildAccessCookie(newAccessToken, Duration.ofMinutes(15));
        ResponseCookie refreshCookie = buildRefreshCookie(result.rawRefreshToken(), Duration.ofDays(7));

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
            .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
            .body(
                Map.of(
                    "message", "Token refreshed successfully",
                    "userId", user.getId(),
                    "role", user.getRole().name()
                )
            );
    }

    /**
     * Logs out the user by revoking the refresh token session and clearing both auth cookies.
     *
     * @param request the incoming servlet request
     * @return HTTP 200 OK with expired cookie headers and confirmation message
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String rawRefreshToken = extractCookie(request, REFRESH_TOKEN_COOKIE_NAME);
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revokeRefreshToken(rawRefreshToken);
        }

        ResponseCookie clearAccessCookie = buildAccessCookie("", Duration.ZERO);
        ResponseCookie clearRefreshCookie = buildRefreshCookie("", Duration.ZERO);

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, clearAccessCookie.toString())
            .header(HttpHeaders.SET_COOKIE, clearRefreshCookie.toString())
            .body(Map.of("message", "Logged out successfully"));
    }

    private ResponseCookie buildAccessCookie(String value, Duration maxAge) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, value)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build();
    }

    private ResponseCookie buildRefreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, value)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Lax")
            .path("/api/auth") // Restrict transmission strictly to authentication endpoints
            .maxAge(maxAge)
            .build();
    }

    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
