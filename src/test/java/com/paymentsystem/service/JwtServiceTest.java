package com.paymentsystem.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying JWT generation, cryptographic validation, expiration handling,
 * role claim extraction, and tamper resistance in {@link JwtService}.
 */
class JwtServiceTest {

    private static final String TEST_SECRET = "super_secret_test_key_for_hmac_sha256_at_least_32_bytes!";
    private static final String DIFFERENT_SECRET = "another_different_secret_key_for_hmac_sha256_at_least_32_bytes!";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET);
    }

    @Test
    @DisplayName("Should generate and validate a valid JWT token, and extract user ID correctly")
    void testValidToken() {
        String userId = "42";
        String token = jwtService.generateToken(userId);

        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertEquals(userId, jwtService.extractUserId(token));
        assertEquals(List.of("ROLE_USER"), jwtService.extractRoles(token));
    }

    @Test
    @DisplayName("Should generate and extract role claims correctly")
    void testRolesClaimExtraction() {
        String token = jwtService.generateToken("99", List.of("ROLE_ADMIN", "ROLE_USER"));

        assertTrue(jwtService.validateToken(token));
        assertEquals("99", jwtService.extractUserId(token));
        List<String> roles = jwtService.extractRoles(token);
        assertEquals(2, roles.size());
        assertTrue(roles.contains("ROLE_ADMIN"));
        assertTrue(roles.contains("ROLE_USER"));
    }

    @Test
    @DisplayName("Should safely return empty list when roles claim is missing or malformed")
    void testMissingOrMalformedRolesClaim() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

        // Token without roles claim
        String tokenWithoutRoles = Jwts.builder()
                .subject("42")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertTrue(jwtService.validateToken(tokenWithoutRoles));
        List<String> roles = jwtService.extractRoles(tokenWithoutRoles);
        assertNotNull(roles);
        assertTrue(roles.isEmpty());

        // Token with non-list roles claim
        String tokenWithWrongTypeRoles = Jwts.builder()
                .subject("42")
                .claim("roles", "NOT_A_LIST")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertTrue(jwtService.validateToken(tokenWithWrongTypeRoles));
        assertTrue(jwtService.extractRoles(tokenWithWrongTypeRoles).isEmpty());
    }

    @Test
    @DisplayName("Should reject an expired JWT token")
    void testExpiredToken() {
        String expiredToken = jwtService.generateToken("42", -10_000);

        assertFalse(jwtService.validateToken(expiredToken));
        assertThrows(IllegalArgumentException.class, () -> jwtService.extractUserId(expiredToken));
        assertTrue(jwtService.extractRoles(expiredToken).isEmpty());
    }

    @Test
    @DisplayName("Should reject a token signed with an invalid signature (different key)")
    void testInvalidSignature() {
        SecretKey otherKey = Keys.hmacShaKeyFor(DIFFERENT_SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenFromOtherKey = Jwts.builder()
                .subject("42")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        assertFalse(jwtService.validateToken(tokenFromOtherKey));
        assertThrows(IllegalArgumentException.class, () -> jwtService.extractUserId(tokenFromOtherKey));
        assertTrue(jwtService.extractRoles(tokenFromOtherKey).isEmpty());
    }

    @Test
    @DisplayName("Should reject a tampered JWT token whose payload was modified")
    void testTamperedToken() {
        String token = jwtService.generateToken("42");
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayloadJson = payloadJson.replace("\"sub\":\"42\"", "\"sub\":\"999\"");
        String tamperedPayloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(
                tamperedPayloadJson.getBytes(StandardCharsets.UTF_8)
        );

        String tamperedToken = parts[0] + "." + tamperedPayloadBase64 + "." + parts[2];

        assertFalse(jwtService.validateToken(tamperedToken));
        assertThrows(IllegalArgumentException.class, () -> jwtService.extractUserId(tamperedToken));
        assertTrue(jwtService.extractRoles(tamperedToken).isEmpty());
    }

    @Test
    @DisplayName("Should reject malformed JWT tokens")
    void testMalformedToken() {
        assertFalse(jwtService.validateToken("not-a-jwt"));
        assertFalse(jwtService.validateToken("header.payload"));
        assertFalse(jwtService.validateToken("header.payload.signature.extra"));
        assertFalse(jwtService.validateToken(""));
        assertFalse(jwtService.validateToken(null));

        assertThrows(IllegalArgumentException.class, () -> jwtService.extractUserId("not-a-jwt"));
        assertThrows(IllegalArgumentException.class, () -> jwtService.extractUserId(null));
        assertThrows(IllegalArgumentException.class, () -> jwtService.extractUserId("   "));
        assertTrue(jwtService.extractRoles(null).isEmpty());
        assertTrue(jwtService.extractRoles("not-a-jwt").isEmpty());
    }

    @Test
    @DisplayName("Should reject a JWT token with a missing or blank subject")
    void testMissingSubject() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

        String tokenWithoutSubject = Jwts.builder()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertFalse(jwtService.validateToken(tokenWithoutSubject));
        assertThrows(IllegalArgumentException.class, () -> jwtService.extractUserId(tokenWithoutSubject));

        String tokenWithBlankSubject = Jwts.builder()
                .subject("   ")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertFalse(jwtService.validateToken(tokenWithBlankSubject));
        assertThrows(IllegalArgumentException.class, () -> jwtService.extractUserId(tokenWithBlankSubject));
    }

    @Test
    @DisplayName("Should reject invalid signing configuration on service initialization")
    void testInvalidSigningConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService(null));
        assertThrows(IllegalArgumentException.class, () -> new JwtService("   "));
        assertThrows(IllegalArgumentException.class, () -> new JwtService("too_short_secret_key_12345678"));
    }

    @Test
    @DisplayName("Should reject null or blank userId when generating token")
    void testGenerateTokenNullOrBlankUserId() {
        assertThrows(IllegalArgumentException.class, () -> jwtService.generateToken(null));
        assertThrows(IllegalArgumentException.class, () -> jwtService.generateToken(""));
        assertThrows(IllegalArgumentException.class, () -> jwtService.generateToken("   "));
    }
}
