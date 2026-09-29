package com.paymentsystem.model;

import java.time.Instant;
import java.util.UUID;

public class IdempotencyKey {
    private final UUID key;
    private final UUID transactionId;
    private final Instant createdAt;
    private final Instant expiresAt;

    public IdempotencyKey(UUID transactionId) {
        this.key = UUID.randomUUID();
        this.transactionId = transactionId;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(30);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public UUID getKey() { return key; }
    public UUID getTransactionId() { return transactionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}