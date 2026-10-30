package com.paymentsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA Entity representing a persisted refresh token in PostgreSQL.
 *
 * <p><strong>Security Architecture:</strong>
 * Only the cryptographic hash (SHA-256) of the refresh token is stored in the database.
 * The raw token string is delivered to the client strictly via an HTTP-only cookie.
 * Supports token rotation, family-based reuse detection, and session invalidation.</p>
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 64)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RefreshToken() {}

    /**
     * Constructs a new refresh token entity record.
     *
     * @param user the associated user
     * @param tokenHash the deterministic SHA-256 hex hash of the raw token
     * @param familyId the UUID grouping tokens in the same rotation session
     * @param expiresAt timestamp when this token becomes invalid
     */
    public RefreshToken(User user, String tokenHash, String familyId, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Checks if the token has been explicitly revoked.
     *
     * @return true if revoked
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Checks if the token has expired.
     *
     * @return true if current time is past expiration
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Checks if the token is currently valid (neither revoked nor expired).
     *
     * @return true if valid
     */
    public boolean isValid() {
        return !isRevoked() && !isExpired();
    }

    /**
     * Revokes the token by stamping the current timestamp.
     */
    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public String getFamilyId() { return familyId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

