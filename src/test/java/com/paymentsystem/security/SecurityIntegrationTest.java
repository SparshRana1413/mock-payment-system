package com.paymentsystem.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentsystem.dto.LoginRequest;
import com.paymentsystem.dto.SignupRequest;
import com.paymentsystem.model.RefreshToken;
import com.paymentsystem.model.Role;
import com.paymentsystem.model.User;
import com.paymentsystem.repository.RefreshTokenRepository;
import com.paymentsystem.repository.UserRepository;
import com.paymentsystem.service.JwtService;
import com.paymentsystem.service.RefreshTokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests verifying Spring Security filter pipelines,
 * dual cookie management (access & refresh), token rotation, reuse detection,
 * and role-based access control (RBAC).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // =========================================================================
    // 1. Protected Endpoints & JWT Authentication Filter Tests
    // =========================================================================

    @Test
    @DisplayName("Protected endpoint without access_token cookie should return HTTP 401 Unauthorized")
    void testProtectedEndpointWithoutCookieReturns401() throws Exception {
        mockMvc.perform(get("/api/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("Protected endpoint with valid JWT cookie should return HTTP 200 OK")
    void testProtectedEndpointWithValidJwtCookieReturns200() throws Exception {
        String token = jwtService.generateToken("101", List.of("ROLE_USER"));

        mockMvc.perform(get("/api/test/protected")
                        .cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.userId").value("101"));
    }

    @Test
    @DisplayName("Protected endpoint with expired JWT cookie should return HTTP 401 Unauthorized")
    void testProtectedEndpointWithExpiredJwtReturns401() throws Exception {
        String expiredToken = jwtService.generateToken("101", List.of("ROLE_USER"), -10_000);

        mockMvc.perform(get("/api/test/protected")
                        .cookie(new Cookie("access_token", expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("Protected endpoint with tampered JWT cookie should return HTTP 401 Unauthorized")
    void testProtectedEndpointWithTamperedJwtReturns401() throws Exception {
        String token = jwtService.generateToken("101", List.of("ROLE_USER"));
        String tamperedToken = token.substring(0, token.lastIndexOf('.') + 1) + "tamperedSignature";

        mockMvc.perform(get("/api/test/protected")
                        .cookie(new Cookie("access_token", tamperedToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Protected endpoint with foreign signature JWT should return HTTP 401 Unauthorized")
    void testProtectedEndpointWithForeignSignatureReturns401() throws Exception {
        SecretKey foreignKey = Keys.hmacShaKeyFor(
                "foreign_secret_key_that_is_at_least_32_bytes_long_123456!".getBytes(StandardCharsets.UTF_8)
        );
        String foreignToken = Jwts.builder()
                .subject("101")
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(foreignKey)
                .compact();

        mockMvc.perform(get("/api/test/protected")
                        .cookie(new Cookie("access_token", foreignToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Protected endpoint with malformed cookie value should return HTTP 401 Unauthorized")
    void testProtectedEndpointWithMalformedJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/test/protected")
                        .cookie(new Cookie("access_token", "random-garbage-string")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Protected endpoint with empty access_token cookie should return HTTP 401 Unauthorized")
    void testProtectedEndpointWithEmptyCookieReturns401() throws Exception {
        mockMvc.perform(get("/api/test/protected")
                        .cookie(new Cookie("access_token", "")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Protected endpoint with unrelated cookies but missing access_token should return HTTP 401 Unauthorized")
    void testProtectedEndpointWithUnrelatedCookiesReturns401() throws Exception {
        mockMvc.perform(get("/api/test/protected")
                        .cookie(new Cookie("session_tracker", "xyz123"), new Cookie("theme", "dark")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // =========================================================================
    // 2. Role-Based Authorization Tests (USER vs ADMIN)
    // =========================================================================

    @Test
    @DisplayName("User with ROLE_USER should be forbidden (403) from accessing admin endpoint")
    void testUserCannotAccessAdminEndpoint() throws Exception {
        String userToken = jwtService.generateToken("201", List.of("ROLE_USER"));

        mockMvc.perform(get("/api/test/admin")
                        .cookie(new Cookie("access_token", userToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("User with ROLE_ADMIN should successfully access admin endpoint (200 OK)")
    void testAdminCanAccessAdminEndpoint() throws Exception {
        String adminToken = jwtService.generateToken("999", List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/test/admin")
                        .cookie(new Cookie("access_token", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Access granted to admin resource"))
                .andExpect(jsonPath("$.userId").value("999"));
    }

    @Test
    @DisplayName("Token missing role claim should default to safe non-privileged access (cannot access admin)")
    void testTokenWithoutRolesCannotAccessAdmin() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(
                "Kj//xJFfjLqojfFpW9/OnoDRbQoon3R7cGWEXJzesS8=".getBytes(StandardCharsets.UTF_8)
        );
        String tokenNoRoles = Jwts.builder()
                .subject("201")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/test/admin")
                        .cookie(new Cookie("access_token", tokenNoRoles)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. Public Endpoints & Registration Tests
    // =========================================================================

    @Test
    @DisplayName("Root endpoint '/' should be publicly accessible without authentication")
    void testRootEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string("<h1>Hello World!</h1>"));
    }

    @Test
    @DisplayName("Signup endpoint should be publicly accessible, assign Role.USER, and not expose password hash")
    void testSignupPublicAndDefaultRole() throws Exception {
        SignupRequest request = new SignupRequest("regularUser", "regular@example.com", "SecurePassword123!");

        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("regularUser"))
                .andExpect(jsonPath("$.email").value("regular@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        // Verify entity in PostgreSQL received Role.USER
        User savedUser = userRepository.findByUsername("regularUser").orElseThrow();
        assertEquals(Role.USER, savedUser.getRole());
    }

    @Test
    @DisplayName("Signup with duplicate username should return 400 Bad Request")
    void testSignupDuplicateUsernameReturns400() throws Exception {
        userRepository.save(new User("dupUser", "user1@example.com", passwordEncoder.encode("pass12345"), Role.USER));

        SignupRequest request = new SignupRequest("dupUser", "user2@example.com", "SecurePassword123!");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    @DisplayName("Signup with duplicate email should return 400 Bad Request")
    void testSignupDuplicateEmailReturns400() throws Exception {
        userRepository.save(new User("existingUser", "dup@example.com", passwordEncoder.encode("pass12345"), Role.USER));

        SignupRequest request = new SignupRequest("differentUser", "dup@example.com", "SecurePassword123!");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    // =========================================================================
    // 4. Login, Refresh Token Rotation, Reuse Detection, and Logout Tests
    // =========================================================================

    @Test
    @DisplayName("Login should return both access_token and refresh_token cookies, and user role")
    void testLoginIssuesBothCookies() throws Exception {
        User user = userRepository.save(
                new User("dualCookieUser", "dual@example.com", passwordEncoder.encode("Password123!"), Role.USER)
        );

        LoginRequest request = new LoginRequest("dual@example.com", "Password123!");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        // Verify refresh token is persisted as hash in database
        Cookie refreshCookie = result.getResponse().getCookie("refresh_token");
        assertNotNull(refreshCookie);
        String rawToken = refreshCookie.getValue();
        String hash = RefreshTokenService.hashToken(rawToken);

        assertTrue(refreshTokenRepository.findByTokenHash(hash).isPresent(),
                "Hashed refresh token must be persisted in database");
    }

    @Test
    @DisplayName("Token refresh endpoint should successfully rotate refresh token and issue new access token")
    void testRefreshTokenRotationSuccess() throws Exception {
        User user = userRepository.save(
                new User("refreshUser", "refresh@example.com", passwordEncoder.encode("Pass123!"), Role.USER)
        );

        RefreshTokenService.IssuedToken issued = refreshTokenService.createRefreshToken(user);

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", issued.rawToken())))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(jsonPath("$.message").value("Token refreshed successfully"))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andReturn();

        Cookie newRefreshCookie = refreshResult.getResponse().getCookie("refresh_token");
        assertNotNull(newRefreshCookie);
        assertNotEquals(issued.rawToken(), newRefreshCookie.getValue(), "Rotated token must be different from previous");

        // Old token must be marked as revoked
        RefreshToken oldRecord = refreshTokenRepository.findByTokenHash(RefreshTokenService.hashToken(issued.rawToken())).orElseThrow();
        assertTrue(oldRecord.isRevoked(), "Old token must be marked revoked");

        // New token must be active
        RefreshToken newRecord = refreshTokenRepository.findByTokenHash(RefreshTokenService.hashToken(newRefreshCookie.getValue())).orElseThrow();
        assertFalse(newRecord.isRevoked(), "New token must be active");
        assertEquals(oldRecord.getFamilyId(), newRecord.getFamilyId(), "Family ID must be preserved");
    }

    @Test
    @DisplayName("Token refresh without refresh_token cookie should return HTTP 401 Unauthorized")
    void testRefreshWithoutCookieReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Refresh token is missing"));
    }

    @Test
    @DisplayName("Token refresh with unknown token should return HTTP 401 Unauthorized")
    void testRefreshWithUnknownTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "unknown-random-token-xyz")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    @DisplayName("Token refresh with expired token should return HTTP 401 Unauthorized")
    void testRefreshWithExpiredTokenReturns401() throws Exception {
        User user = userRepository.save(
                new User("expUser", "exp@example.com", passwordEncoder.encode("Pass123!"), Role.USER)
        );

        String rawToken = "expired_raw_token_test";
        String hash = RefreshTokenService.hashToken(rawToken);
        RefreshToken expired = new RefreshToken(user, hash, "family-exp", LocalDateTime.now().minusMinutes(5));
        refreshTokenRepository.save(expired);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", rawToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Refresh token has expired"));
    }

    @Test
    @DisplayName("Reusing an old rotated refresh token should trigger reuse detection and revoke family session")
    void testRefreshTokenReuseDetection() throws Exception {
        User user = userRepository.save(
                new User("reuseUser", "reuse@example.com", passwordEncoder.encode("Pass123!"), Role.USER)
        );

        RefreshTokenService.IssuedToken issued = refreshTokenService.createRefreshToken(user);

        // First refresh: rotates successfully
        MvcResult firstRefresh = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", issued.rawToken())))
                .andExpect(status().isOk())
                .andReturn();

        Cookie rotatedCookie = firstRefresh.getResponse().getCookie("refresh_token");
        assertNotNull(rotatedCookie);

        // Second refresh ATTEMPT WITH OLD TOKEN: triggers reuse detection!
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", issued.rawToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Refresh token reuse detected. Family session revoked."));

        // Now even the rotated new token should be revoked because the entire family was invalidated!
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", rotatedCookie.getValue())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Expired access token can still be renewed using a valid refresh token")
    void testExpiredAccessTokenRenewedByRefreshToken() throws Exception {
        User user = userRepository.save(
                new User("renewUser", "renew@example.com", passwordEncoder.encode("Pass123!"), Role.USER)
        );

        RefreshTokenService.IssuedToken issued = refreshTokenService.createRefreshToken(user);
        String expiredAccessToken = jwtService.generateToken(user.getId().toString(), List.of("ROLE_USER"), -10_000);

        // Access token is rejected on protected endpoint
        mockMvc.perform(get("/api/test/protected")
                        .cookie(new Cookie("access_token", expiredAccessToken)))
                .andExpect(status().isUnauthorized());

        // Refresh token successfully renews access token
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", issued.rawToken())))
                .andExpect(status().isOk())
                .andReturn();

        Cookie newAccessCookie = refreshResult.getResponse().getCookie("access_token");
        assertNotNull(newAccessCookie);

        // New access token works on protected endpoint
        mockMvc.perform(get("/api/test/protected")
                        .cookie(new Cookie("access_token", newAccessCookie.getValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.userId").value(user.getId().toString()));
    }

    @Test
    @DisplayName("Logout endpoint should revoke refresh token session and clear both cookies")
    void testLogoutClearsBothCookiesAndRevokesSession() throws Exception {
        User user = userRepository.save(
                new User("logoutUser", "logout@example.com", passwordEncoder.encode("Pass123!"), Role.USER)
        );

        RefreshTokenService.IssuedToken issued = refreshTokenService.createRefreshToken(user);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refresh_token", issued.rawToken())))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // Verify token was revoked in database
        RefreshToken tokenRecord = refreshTokenRepository.findByTokenHash(
                RefreshTokenService.hashToken(issued.rawToken())
        ).orElseThrow();
        assertTrue(tokenRecord.isRevoked(), "Token must be revoked in database upon logout");
    }
}
