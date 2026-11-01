package com.paymentsystem.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * JPA Entity representing an end-to-end payment transaction in the PostgreSQL database.
 *
 * <p><strong>Architectural Role:</strong>
 * Domain Model / Persistence Entity layer. Maps directly to the {@code transactions} table
 * created by Flyway migration {@code V3__create_transactions.sql}.
 * Models the lifecycle, state transitions (PENDING &rarr; HELD &rarr; COMPLETED/FAILED),
 * idempotency key binding, and historical audit trail of bank RPC attempts for a payment.</p>
 */
@Entity
@Table(
    name = "transactions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_transactions_idempotency_key", columnNames = "idempotency_key")
    }
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "from_account", nullable = false, length = 255)
    private String fromAccount;

    @Column(name = "to_account", nullable = false, length = 255)
    private String toAccount;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "hold_id", length = 255)
    private String holdId;

    @Column(name = "capture_txn_id", length = 255)
    private String captureTxnId;

    @Column(name = "credit_txn_id", length = 255)
    private String creditTxnId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Transient
    private final List<BankCallAttempt> attempts = new ArrayList<>();

    /**
     * Default no-argument constructor required by JPA/Hibernate proxy instantiation.
     */
    protected Transaction() {}

    /**
     * Constructs a Transaction instance using Builder parameters.
     */
    private Transaction(Builder b) {
        this.id = b.id;
        this.idempotencyKey = b.idempotencyKey != null ? b.idempotencyKey : UUID.randomUUID().toString();
        this.fromAccount = b.fromAccount;
        this.toAccount = b.toAccount;
        this.amount = b.amount;
        this.currency = b.currency != null ? b.currency : "USD";
        this.status = b.status != null ? b.status : TransactionStatus.PENDING;
        this.holdId = b.holdId;
        this.captureTxnId = b.captureTxnId;
        this.creditTxnId = b.creditTxnId;
        this.createdAt = b.createdAt != null ? b.createdAt : Instant.now();
        this.expiresAt = b.expiresAt != null ? b.expiresAt : this.createdAt.plusSeconds(30);
        this.completedAt = b.completedAt;
    }

    /**
     * Convenience constructor initializing required business fields.
     */
    public Transaction(String idempotencyKey, String fromAccount, String toAccount,
                       BigDecimal amount, String currency) {
        this.idempotencyKey = idempotencyKey;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.currency = currency;
        this.status = TransactionStatus.PENDING;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(30);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (expiresAt == null) {
            expiresAt = createdAt.plusSeconds(30);
        }
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
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
     */
    public static class Builder {
        private UUID id;
        private String idempotencyKey = UUID.randomUUID().toString();
        private String fromAccount;
        private String toAccount;
        private BigDecimal amount;
        private String currency = "USD";
        private TransactionStatus status = TransactionStatus.PENDING;
        private String holdId;
        private String captureTxnId;
        private String creditTxnId;
        private Instant createdAt;
        private Instant expiresAt;
        private Instant completedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder transactionId(UUID transactionId) { this.id = transactionId; return this; }
        public Builder idempotencyKey(String v) { this.idempotencyKey = v; return this; }
        public Builder idempotencyKey(UUID v) { this.idempotencyKey = (v != null ? v.toString() : null); return this; }
        public Builder fromAccount(String v) { this.fromAccount = v; return this; }
        public Builder toAccount(String v) { this.toAccount = v; return this; }
        public Builder amount(BigDecimal v) { this.amount = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder status(TransactionStatus v) { this.status = v; return this; }
        public Builder holdId(String v) { this.holdId = v; return this; }
        public Builder captureTxnId(String v) { this.captureTxnId = v; return this; }
        public Builder creditTxnId(String v) { this.creditTxnId = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder expiresAt(Instant v) { this.expiresAt = v; return this; }
        public Builder completedAt(Instant v) { this.completedAt = v; return this; }

        /**
         * Validates required parameters and builds the {@link Transaction}.
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
        this.status = newStatus;
        if (newStatus == TransactionStatus.COMPLETED || newStatus == TransactionStatus.REVERSED
                || newStatus == TransactionStatus.FAILED) {
            if (this.completedAt == null) {
                this.completedAt = Instant.now();
            }
        }
    }

    /**
     * Checks whether the transaction has exceeded its validity window.
     *
     * @return true if the current time is after {@code expiresAt}, false otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getTransactionId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getFromAccount() { return fromAccount; }
    public String getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public TransactionStatus getStatus() { return status; }
    public String getHoldId() { return holdId; }
    public String getCaptureTxnId() { return captureTxnId; }
    public String getCreditTxnId() { return creditTxnId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCompletedAt() { return completedAt; }
    public List<BankCallAttempt> getAttempts() { return Collections.unmodifiableList(attempts); }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setFromAccount(String fromAccount) { this.fromAccount = fromAccount; }
    public void setToAccount(String toAccount) { this.toAccount = toAccount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public void setHoldId(String holdId) { this.holdId = holdId; }
    public void setCaptureTxnId(String captureTxnId) { this.captureTxnId = captureTxnId; }
    public void setCreditTxnId(String creditTxnId) { this.creditTxnId = creditTxnId; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
