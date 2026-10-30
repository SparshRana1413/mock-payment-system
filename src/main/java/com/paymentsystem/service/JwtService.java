
package com.paymentsystem.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Service responsible for cryptographic JSON Web Token (JWT) generation and signing.
 *
 * <p><strong>Architectural Role:</strong>
 * Infrastructure / Security Service layer. Manages token cryptographic signing using HMAC-SHA256
 * and encodes user identity into compact JWT tokens.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a JWT helper module utilizing the {@code jsonwebtoken} library
 * (e.g., {@code jwt.sign({ sub: userId }, process.env.JWT_SECRET, { expiresIn: '15m' })}).</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Injected into {@link com.paymentsystem.controller.LoginController} to issue authentication tokens
 * upon successful login.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Cookie wrapping and HTTP transport belong in {@link com.paymentsystem.controller.LoginController};
 * user credential verification belongs in {@link LoginService}.</p>
 */
@Service
public class JwtService {

    private final SecretKey key;

    /**
     * Constructs the JWT service by deriving an HMAC-SHA signing key from application configuration.
     *
     * <p>The {@code @Value("${jwt.secret}")} annotation binds configuration properties
     * (from {@code application.properties} or environment variables) into constructor parameters,
     * analogous to reading {@code process.env.JWT_SECRET} in Node.js.</p>
     *
     * @param secret the raw secret key string used for HMAC signing
     */
    public JwtService(
            @Value("${jwt.secret}") String secret) {

        // Creates an HMAC-SHA cryptographic key from the UTF-8 bytes of the configured secret string
        this.key = Keys.hmacShaKeyFor(
            secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Generates a signed JWT with a 15-minute expiration time.
     *
     * <p>Populates standard JWT registered claims: {@code sub} (Subject = userId),
     * {@code iat} (Issued At), and {@code exp} (Expiration = now + 15 minutes).</p>
     *
     * @param userId the user ID string to set as the JWT subject claim
     * @return the serialized, cryptographically signed JWT token string
     */
    public String generateToken(String userId) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
            .subject(userId)
            .issuedAt(new Date(now))
            .expiration(new Date(now + 15 * 60 * 1000)) // 15-minute validity window
            .signWith(key)
            .compact();
    }
}
