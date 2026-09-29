package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.Money;

/**
 * Request DTO for the bank's Hold RPC. Immutable — the idempotency key is
 * decided once by the caller (derived from your TransactionId + "HOLD")
 * and passed in; this class never generates its own.
 */
public class HoldRequest {

    private final UUID idempotencyKey;
    private final UUID accountId;
    private final Money amount;
    private final long ttlSeconds; // always sent explicitly, never relying on a default
    private final UUID transactionReference; // your Payment System's TransactionId

    public HoldRequest(UUID idempotencyKey, UUID accountId, Money amount, long ttlSeconds, UUID transactionReference) {
        this.idempotencyKey = idempotencyKey;
        this.accountId = accountId;
        this.amount = amount;
        this.ttlSeconds = ttlSeconds;
        this.transactionReference = transactionReference;
    }

    public UUID getIdempotencyKey() { return idempotencyKey; }
    public UUID getAccountId() { return accountId; }
    public Money getAmount() { return amount; }
    public long getTtlSeconds() { return ttlSeconds; }
    public UUID getTransactionReference() { return transactionReference; }
}