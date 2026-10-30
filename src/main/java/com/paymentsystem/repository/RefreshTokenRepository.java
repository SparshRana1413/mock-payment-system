package com.paymentsystem.repository;

import com.paymentsystem.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link RefreshToken} operations.
 *
 * <p>Provides pessimistic locking for safe concurrent token rotation,
 * and bulk modification queries for family-wide reuse revocation.</p>
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Finds a refresh token by its deterministic SHA-256 hash.
     *
     * @param tokenHash the hashed token string
     * @return an {@link Optional} containing the found {@link RefreshToken}
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Finds a refresh token by its hash, applying a pessimistic write lock (SELECT ... FOR UPDATE)
     * to prevent race conditions during concurrent refresh attempts.
     *
     * @param tokenHash the hashed token string
     * @return an {@link Optional} containing the locked {@link RefreshToken}
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r JOIN FETCH r.user WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashWithLock(@Param("tokenHash") String tokenHash);

    /**
     * Revokes all active tokens within a family upon detecting token reuse/replay attacks.
     *
     * @param familyId the token family identifier
     * @return count of revoked tokens
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedAt = CURRENT_TIMESTAMP WHERE r.familyId = :familyId AND r.revokedAt IS NULL")
    int revokeAllByFamilyId(@Param("familyId") String familyId);

    /**
     * Revokes all active refresh tokens belonging to a user (used during global logout).
     *
     * @param userId primary key of the user
     * @return count of revoked tokens
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedAt = CURRENT_TIMESTAMP WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId);
}

