package com.paymentsystem.model;

import java.time.Instant;
import java.util.UUID;

public class BankCallAttempt {
    private final UUID attemptId;
    private final UUID transactionId;
    private final Step step;
    private final int retryNumber;
    private final Instant attemptedAt;
    private final AttemptResult result;
    private final String bankResponseRaw;

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

    // getters only, no setters — this is a historical record, never changes
    public UUID getAttemptId() { return attemptId; }
    public UUID getTransactionId() { return transactionId; }
    public Step getStep() { return step; }
    public int getRetryNumber() { return retryNumber; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public AttemptResult getResult() { return result; }
    public String getBankResponseRaw() { return bankResponseRaw; }
}