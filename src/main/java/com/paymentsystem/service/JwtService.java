package com.paymentsystem.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Service responsible for cryptographic JSON Web Token (JWT) generation, signing, and verification.
 *
 * <p><strong>Architectural Role:</strong>
 * Infrastructure / Security Service layer. Manages HMAC-SHA256 signing and validation,
 * token expiration checks, and safe user identity and role extraction.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a JWT helper module utilizing {@code jsonwebtoken}
 * (e.g., {@code jwt.sign({ sub: userId, roles: ['ROLE_USER'] }, secret, { expiresIn: '15m' })}).</p>
 */
@Service
public class JwtService {

    public static final long DEFAULT_EXPIRATION_MILLIS = 15 * 60 * 1000; // 15 minutes TTL

    private final SecretKey key;
    private final JwtParser jwtParser;

    /**
     * Constructs the JWT service by deriving an HMAC-SHA signing key from application configuration.
     *
     * <p>Fails fast during startup if the secret key is missing, blank, or shorter than 256 bits (32 bytes).</p>
     *
     * @param secret the raw secret key string used for HMAC-SHA signing
     */
    public JwtService(@Value("${jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret key must not be null or blank");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 256 bits (32 bytes)");
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.jwtParser = Jwts.parser().verifyWith(this.key).build();
    }

    /**
     * Generates a signed JWT with user ID and default ROLE_USER with a 15-minute expiration.
     *
     * @param userId the user ID string to set as the JWT subject claim
     * @return the serialized, cryptographically signed JWT token string
     */
    public String generateToken(String userId) {
        return generateToken(userId, List.of("ROLE_USER"), DEFAULT_EXPIRATION_MILLIS);
    }

    /**
     * Generates a signed JWT with user ID and custom expiration.
     *
     * @param userId the user ID string to set as the JWT subject claim
     * @param expirationMillis expiration duration in milliseconds
     * @return the serialized, cryptographically signed JWT token string
     */
    public String generateToken(String userId, long expirationMillis) {
        return generateToken(userId, List.of("ROLE_USER"), expirationMillis);
    }

    /**
     * Generates a signed JWT containing user ID and granted role authorities with default 15-minute TTL.
     *
     * @param userId the user ID string
     * @param roles list of authorities (e.g. {@code ["ROLE_USER", "ROLE_ADMIN"]})
     * @return the signed JWT string
     */
    public String generateToken(String userId, List<String> roles) {
        return generateToken(userId, roles, DEFAULT_EXPIRATION_MILLIS);
    }

    /**
     * Generates a signed JWT containing user ID, roles, and a custom expiration window.
     *
     * @param userId the user ID string
     * @param roles list of authorities
     * @param expirationMillis expiration duration in milliseconds
     * @return the signed JWT string
     */
    public String generateToken(String userId, List<String> roles, long expirationMillis) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be null or blank");
        }

        long now = System.currentTimeMillis();
        List<String> authorities = (roles != null) ? roles : Collections.emptyList();

        return Jwts.builder()
                .subject(userId)
                .claim("roles", authorities)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMillis))
                .signWith(key)
                .compact();
    }

    /**
     * Cryptographically validates token signature, expiration, formatting, and non-empty subject.
     *
     * @param token the candidate JWT string
     * @return true if the token is non-expired, untampered, properly signed, and has a valid subject
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            String subject = claims.getSubject();
            return subject != null && !subject.isBlank();
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extracts the user ID (subject claim) from a valid JWT.
     *
     * @param token the candidate JWT string
     * @return the user ID contained in the subject claim
     * @throws IllegalArgumentException if the token is null, invalid, expired, or has a blank subject
     */
    public String extractUserId(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be null or blank");
        }

        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new IllegalArgumentException("Token subject is missing or blank");
            }
            return subject;
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid JWT token: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts granted authorities from the {@code roles} claim.
     *
     * <p>If the claim is missing, malformed, or unparseable, returns an empty list.
     * Never elevates privileges to administrative roles on error.</p>
     *
     * @param token the candidate JWT string
     * @return list of role authority strings (e.g. {@code ["ROLE_USER"]}), never null
     */
    public List<String> extractRoles(String token) {
        if (token == null || token.isBlank()) {
            return Collections.emptyList();
        }

        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            Object rolesObj = claims.get("roles");
            if (rolesObj instanceof List<?> list) {
                return list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
            }
            return Collections.emptyList();
        } catch (JwtException | IllegalArgumentException e) {
            return Collections.emptyList();
        }
    }
}
