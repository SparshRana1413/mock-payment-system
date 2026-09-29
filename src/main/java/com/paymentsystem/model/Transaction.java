package com.paymentsystem.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public class Transaction {
    private final UUID transactionId;
    private final UUID idempotencyKey;
    private final String fromAccount;
    private final String toAccount;
    private final BigDecimal amount;
    private TransactionStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant completedAt;
    private final List<BankCallAttempt> attempts = new ArrayList<>();

    // Private constructor — only the builder can call this
    private Transaction(Builder b) {
        this.transactionId = b.transactionId;
        this.idempotencyKey = b.idempotencyKey;
        this.fromAccount = b.fromAccount;
        this.toAccount = b.toAccount;
        this.amount = b.amount;
        this.status = TransactionStatus.PENDING;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(30);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID transactionId = UUID.randomUUID();
        private UUID idempotencyKey = UUID.randomUUID();
        private String fromAccount;
        private String toAccount;
        private BigDecimal amount;

        public Builder fromAccount(String v) { this.fromAccount = v; return this; }
        public Builder toAccount(String v) { this.toAccount = v; return this; }
        public Builder amount(BigDecimal v) { this.amount = v; return this; }

        public Transaction build() {
            if (fromAccount == null || toAccount == null || amount == null) {
                throw new IllegalStateException("fromAccount, toAccount, and amount are required");
            }
            return new Transaction(this);
        }
    }

    // Controlled state transitions — NOT raw setters
    public void recordAttempt(BankCallAttempt attempt) {
        this.attempts.add(attempt);
    }

    public void markStatus(TransactionStatus newStatus) {
        // optionally validate legal transitions here (PENDING -> HELD -> ... )
        this.status = newStatus;
        if (newStatus == TransactionStatus.COMPLETED || newStatus == TransactionStatus.REVERSED
                || newStatus == TransactionStatus.FAILED) {
            this.completedAt = Instant.now();
        }
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    // Getters only — no public setters for fromAccount/toAccount/amount/transactionId
    public UUID getTransactionId() { return transactionId; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public String getFromAccount() { return fromAccount; }
    public String getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public TransactionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCompletedAt() { return completedAt; }
    public List<BankCallAttempt> getAttempts() { return Collections.unmodifiableList(attempts); }

    // Usage:
    // Transaction txn = Transaction.builder()
    //     .fromAccount("acc-123")
    //     .toAccount("acc-456")
    //     .amount(new BigDecimal("500.00"))
    //     .build();
}