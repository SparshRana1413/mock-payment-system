
package com.paymentsystem.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(
            @Value("${jwt.secret}") String secret) {

        this.key = Keys.hmacShaKeyFor(
            secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateToken(String userId) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
            .subject(userId)
            .issuedAt(new Date(now))
            .expiration(new Date(now + 15 * 60 * 1000))
            .signWith(key)
            .compact();
    }
}
