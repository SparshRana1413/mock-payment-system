package com.paymentsystem.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing an idempotency key used to prevent duplicate payment executions.
 *
 * <p><strong>Architectural Role:</strong>
 * Core Domain / Safety layer. Implements ADR-004 ("Idempotency Requirements for Financial Modifications").
 * Binds an external or internally generated unique key to a specific {@link Transaction} with a
 * strict time-to-live (TTL) window.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to an idempotency record stored in Redis or MongoDB in an Express API
 * (e.g. tracking incoming {@code Idempotency-Key} headers to guarantee exactly-once processing).</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Bank-side idempotency tracking is managed by the mock bank service; local retry orchestration
 * belongs in the transaction service layer.</p>
 */
public class IdempotencyKey {
    private final UUID key;
    private final UUID transactionId;
    private final Instant createdAt;
    private final Instant expiresAt;

    /**
     * Constructs a new IdempotencyKey bound to a transaction with a 30-second TTL.
     *
     * @param transactionId the unique ID of the transaction this key protects
     */
    public IdempotencyKey(UUID transactionId) {
        this.key = UUID.randomUUID();
        this.transactionId = transactionId;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(30);
    }

    /**
     * Checks if this idempotency key has exceeded its validity window.
     *
     * @return true if the current instant is after the expiration instant, false otherwise
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public UUID getKey() { return key; }
    public UUID getTransactionId() { return transactionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}