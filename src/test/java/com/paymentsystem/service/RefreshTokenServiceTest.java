package com.paymentsystem.service;

import com.paymentsystem.model.RefreshToken;
import com.paymentsystem.model.Role;
import com.paymentsystem.model.User;
import com.paymentsystem.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying refresh token issuance, deterministic SHA-256 hashing,
 * atomic rotation, expiration checks, and token family reuse detection.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private User testUser;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, 604800000L); // 7 days
        testUser = new User("alice", "alice@example.com", "hash", Role.USER);
    }

    @Test
    @DisplayName("Should successfully issue and persist a hashed refresh token")
    void testCreateRefreshToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshTokenService.IssuedToken issued = refreshTokenService.createRefreshToken(testUser);

        assertNotNull(issued);
        assertNotNull(issued.rawToken());
        assertFalse(issued.rawToken().isBlank());
        assertNotNull(issued.entity());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertEquals(RefreshTokenService.hashToken(issued.rawToken()), saved.getTokenHash());
        assertNotNull(saved.getFamilyId());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
        assertFalse(saved.isRevoked());
    }

    @Test
    @DisplayName("Should rotate a valid refresh token and preserve family ID")
    void testRotateRefreshTokenSuccess() {
        String rawToken = "sample_raw_refresh_token_string_12345";
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        String familyId = "family-uuid-123";

        RefreshToken existing = new RefreshToken(
                testUser,
                tokenHash,
                familyId,
                LocalDateTime.now().plusDays(5)
        );

        when(refreshTokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        RefreshTokenService.RefreshResult result = refreshTokenService.rotateRefreshToken(rawToken);

        assertNotNull(result);
        assertNotNull(result.rawRefreshToken());
        assertNotEquals(rawToken, result.rawRefreshToken());
        assertEquals(testUser, result.user());
        assertTrue(existing.isRevoked(), "Original token must be revoked upon rotation");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken replacement = captor.getValue();

        assertEquals(familyId, replacement.getFamilyId(), "Replacement token must inherit the same family ID");
        assertEquals(RefreshTokenService.hashToken(result.rawRefreshToken()), replacement.getTokenHash());
    }

    @Test
    @DisplayName("Should detect reuse of an already-revoked token and invalidate the entire family")
    void testReuseDetectionRevokesFamily() {
        String rawToken = "stolen_old_raw_refresh_token";
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        String familyId = "family-compromised-456";

        RefreshToken revokedToken = new RefreshToken(
                testUser,
                tokenHash,
                familyId,
                LocalDateTime.now().plusDays(5)
        );
        revokedToken.revoke(); // Already revoked previously

        when(refreshTokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(revokedToken));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
            refreshTokenService.rotateRefreshToken(rawToken)
        );

        assertTrue(ex.getMessage().contains("reuse detected"));
        verify(refreshTokenRepository).revokeAllByFamilyId(familyId);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should reject an expired refresh token")
    void testExpiredRefreshTokenRejected() {
        String rawToken = "expired_raw_token";
        String tokenHash = RefreshTokenService.hashToken(rawToken);

        RefreshToken expiredToken = new RefreshToken(
                testUser,
                tokenHash,
                "family-789",
                LocalDateTime.now().minusHours(1) // Expired 1 hour ago
        );

        when(refreshTokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(expiredToken));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
            refreshTokenService.rotateRefreshToken(rawToken)
        );

        assertTrue(ex.getMessage().contains("expired"));
        assertTrue(expiredToken.isRevoked());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should reject unknown or missing refresh tokens")
    void testUnknownOrMissingRefreshTokenRejected() {
        assertThrows(BadCredentialsException.class, () -> refreshTokenService.rotateRefreshToken(null));
        assertThrows(BadCredentialsException.class, () -> refreshTokenService.rotateRefreshToken("   "));

        String rawToken = "non_existent_token";
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        when(refreshTokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> refreshTokenService.rotateRefreshToken(rawToken));
    }

    @Test
    @DisplayName("Should revoke token family on explicit logout")
    void testRevokeRefreshTokenOnLogout() {
        String rawToken = "logout_raw_token";
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        String familyId = "logout-family-111";

        RefreshToken token = new RefreshToken(testUser, tokenHash, familyId, LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        refreshTokenService.revokeRefreshToken(rawToken);

        verify(refreshTokenRepository).revokeAllByFamilyId(familyId);
    }

    @Test
    @DisplayName("Should produce consistent SHA-256 hash for identical tokens and distinct hashes for distinct tokens")
    void testHashConsistency() {
        String tokenA = "token_alpha_12345";
        String tokenB = "token_beta_67890";

        assertEquals(RefreshTokenService.hashToken(tokenA), RefreshTokenService.hashToken(tokenA));
        assertNotEquals(RefreshTokenService.hashToken(tokenA), RefreshTokenService.hashToken(tokenB));
        assertEquals(64, RefreshTokenService.hashToken(tokenA).length());
    }
}

