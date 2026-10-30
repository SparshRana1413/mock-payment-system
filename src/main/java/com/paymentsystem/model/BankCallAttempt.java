package com.paymentsystem.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record capturing a single bank RPC invocation attempt.
 *
 * <p><strong>Architectural Role:</strong>
 * Core Domain / Audit layer. Stored as an audit trail inside {@link Transaction}
 * to document the exact sequence of RPC attempts, retry counts, outcomes, and raw bank responses.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to an audit logging document in MongoDB or PostgreSQL recording third-party API
 * HTTP/RPC attempts for traceability and reconciliation.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Retry decisions and backoff intervals belong in the transaction orchestration service.</p>
 */
public class BankCallAttempt {
    private final UUID attemptId;
    private final UUID transactionId;
    private final Step step;
    private final int retryNumber;
    private final Instant attemptedAt;
    private final AttemptResult result;
    private final String bankResponseRaw;

    /**
     * Constructs an immutable attempt record.
     *
     * @param transactionId the ID of the parent payment transaction
     * @param step the banking workflow step (e.g. HOLD, CAPTURE)
     * @param retryNumber the zero-indexed retry attempt count
     * @param result the outcome of the attempt (SUCCESS or FAILED)
     * @param bankResponseRaw the raw response payload or error string from the bank
     */
    public BankCallAttempt(UUID transactionId, Step step, int retryNumber,
                            AttemptResult result, String bankResponseRaw) {
        this.attemptId = UUID.randomUUID();
        this.transactionId = transactionId;
        this.step = step;
        this.retryNumber = retryNumber;
        this.attemptedAt = Instant.now();
        this.result = result;
        this.bankResponseRaw = bankResponseRaw;
    }

    // Getters only, no setters — this is a historical fact record that never changes
    public UUID getAttemptId() { return attemptId; }
    public UUID getTransactionId() { return transactionId; }
    public Step getStep() { return step; }
    public int getRetryNumber() { return retryNumber; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public AttemptResult getResult() { return result; }
    public String getBankResponseRaw() { return bankResponseRaw; }
}