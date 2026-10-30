package com.paymentsystem.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Domain aggregate root representing an end-to-end payment transaction.
 *
 * <p><strong>Architectural Role:</strong>
 * Core Domain layer. Models the lifecycle, state transitions (PENDING &rarr; HELD &rarr; COMPLETED/FAILED),
 * idempotency key binding, and historical audit trail of bank RPC attempts for a payment.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a domain entity / aggregate root in TypeScript Clean Architecture or DDD.
 * Rather than exposing mutable public properties, state transitions are governed by explicit
 * domain methods like {@link #markStatus(TransactionStatus)} and {@link #recordAttempt(BankCallAttempt)}.</p>
 *
 * <p><strong>Component Interactions:</strong>
 * Orchestrated by future payment/saga services, converted to DTOs via
 * {@link com.paymentsystem.dto.TransactionResponse}, and coordinates state with {@link BankCallAttempt}.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Low-level gRPC transport belongs in {@link com.paymentsystem.bank.BankClient}; database persistence
 * belongs in a TransactionRepository.</p>
 */
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

    // Private constructor — only the builder can call this to enforce complete instantiation
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

    /**
     * Creates a new Builder instance to construct a {@link Transaction}.
     *
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder pattern implementation for {@link Transaction}.
     *
     * <p>In Java, the Builder pattern is commonly used in place of TypeScript's object literals
     * or partial options objects to provide fluent, type-safe object construction with validation.</p>
     */
    public static class Builder {
        private UUID transactionId = UUID.randomUUID();
        private UUID idempotencyKey = UUID.randomUUID();
        private String fromAccount;
        private String toAccount;
        private BigDecimal amount;

        public Builder fromAccount(String v) { this.fromAccount = v; return this; }
        public Builder toAccount(String v) { this.toAccount = v; return this; }
        public Builder amount(BigDecimal v) { this.amount = v; return this; }

        /**
         * Validates required parameters and builds the immutable {@link Transaction}.
         *
         * @return a validated {@link Transaction} instance
         * @throws IllegalStateException if any required field is missing
         */
        public Transaction build() {
            if (fromAccount == null || toAccount == null || amount == null) {
                throw new IllegalStateException("fromAccount, toAccount, and amount are required");
            }
            return new Transaction(this);
        }
    }

    /**
     * Appends a bank RPC invocation record to this transaction's attempt history.
     *
     * @param attempt the attempt record to log
     */
    public void recordAttempt(BankCallAttempt attempt) {
        this.attempts.add(attempt);
    }

    /**
     * Transitions the transaction into a new lifecycle status.
     *
     * <p>If transitioning to a terminal state (COMPLETED, REVERSED, or FAILED),
     * automatically records the {@code completedAt} timestamp.</p>
     *
     * @param newStatus the new {@link TransactionStatus}
     */
    public void markStatus(TransactionStatus newStatus) {
        // Controlled state transitions — NOT raw setters
        this.status = newStatus;
        if (newStatus == TransactionStatus.COMPLETED || newStatus == TransactionStatus.REVERSED
                || newStatus == TransactionStatus.FAILED) {
            this.completedAt = Instant.now();
        }
    }

    /**
     * Checks whether the transaction has exceeded its 30-second validity window.
     *
     * @return true if the current time is after {@code expiresAt}, false otherwise
     */
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

    /**
     * Returns an unmodifiable view of the bank attempt history to preserve encapsulation.
     *
     * @return immutable {@link List} of {@link BankCallAttempt}
     */
    public List<BankCallAttempt> getAttempts() { return Collections.unmodifiableList(attempts); }

}