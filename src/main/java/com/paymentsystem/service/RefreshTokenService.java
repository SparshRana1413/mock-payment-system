package com.paymentsystem.service;

import com.paymentsystem.model.RefreshToken;
import com.paymentsystem.model.User;
import com.paymentsystem.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Service managing cryptographically secure refresh token issuance, persistence,
 * atomic rotation, and token family reuse detection.
 *
 * <p><strong>Security Architecture:</strong>
 * Raw refresh tokens are 256-bit secure random strings generated via {@link SecureRandom}.
 * The database strictly stores deterministic SHA-256 hashes of these tokens to defend against
 * database compromise. Token rotation is enforced on every refresh request, and reuse of an already
 * rotated token invalidates the entire token family session.</p>
 */
@Service
public class RefreshTokenService {

    public static final long DEFAULT_REFRESH_EXPIRATION_MS = 7L * 24 * 60 * 60 * 1000; // 7 days

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom;
    private final long expirationMs;

    /**
     * Carrier record returning the new raw refresh token and associated user upon rotation.
     */
    public record RefreshResult(
        String rawRefreshToken,
        User user
    ) {}

    /**
     * Carrier record returning initial raw token and persisted entity.
     */
    public record IssuedToken(
        String rawToken,
        RefreshToken entity
    ) {}

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-token.expiration-ms:604800000}") long expirationMs) {

        this.refreshTokenRepository = refreshTokenRepository;
        this.secureRandom = new SecureRandom();
        this.expirationMs = expirationMs;
    }

    /**
     * Issues and persists a new initial refresh token for an authenticated user.
     *
     * @param user the authenticated user
     * @return the issued token record containing the raw token and persisted entity
     */
    @Transactional
    public IssuedToken createRefreshToken(User user) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        String familyId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plus(expirationMs, ChronoUnit.MILLIS);

        RefreshToken refreshToken = new RefreshToken(user, tokenHash, familyId, expiresAt);
        RefreshToken saved = refreshTokenRepository.save(refreshToken);

        return new IssuedToken(rawToken, saved);
    }

    /**
     * Rotates a refresh token: invalidates the old token, checks for reuse, and issues a replacement.
     *
     * <p>Uses pessimistic write locking to prevent race conditions during concurrent refresh requests.
     * If an already revoked token is presented, the entire token family is immediately revoked.</p>
     *
     * @param rawRefreshToken the raw token presented by the client
     * @return {@link RefreshResult} with new raw token and user details
     * @throws BadCredentialsException if token is missing, unknown, expired, or reused
     */
    @Transactional
    public RefreshResult rotateRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException("Refresh token is missing or blank");
        }

        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken currentToken = refreshTokenRepository.findByTokenHashWithLock(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        // Reuse detection: If the token was already revoked, someone is replaying an old token!
        if (currentToken.isRevoked()) {
            refreshTokenRepository.revokeAllByFamilyId(currentToken.getFamilyId());
            throw new BadCredentialsException("Refresh token reuse detected. Family session revoked.");
        }

        // Check expiration
        if (currentToken.isExpired()) {
            currentToken.revoke();
            throw new BadCredentialsException("Refresh token has expired");
        }

        // Atomically invalidate current token
        currentToken.revoke();

        // Issue new replacement token in the same family
        String newRawToken = generateRawToken();
        String newTokenHash = hashToken(newRawToken);
        LocalDateTime newExpiresAt = LocalDateTime.now().plus(expirationMs, ChronoUnit.MILLIS);

        RefreshToken replacementToken = new RefreshToken(
                currentToken.getUser(),
                newTokenHash,
                currentToken.getFamilyId(),
                newExpiresAt
        );

        refreshTokenRepository.save(replacementToken);

        return new RefreshResult(newRawToken, currentToken.getUser());
    }

    /**
     * Revokes a refresh token session during logout.
     *
     * @param rawRefreshToken the presented raw refresh token (may be null)
     */
    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> refreshTokenRepository.revokeAllByFamilyId(token.getFamilyId()));
    }

    /**
     * Generates a 32-byte (256-bit) cryptographically secure URL-safe random string.
     */
    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Computes a deterministic SHA-256 hexadecimal hash of the raw token.
     *
     * @param rawToken the raw token string
     * @return 64-character lowercase hexadecimal hash
     */
    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 cryptographic algorithm is unavailable", e);
        }
    }
}

