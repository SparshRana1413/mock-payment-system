package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.Money;

/**
 * Request DTO for the Mock Bank's Hold RPC operation.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / DTO layer. Initiates phase one of a two-phase payment (Auth/Capture),
 * requesting the bank to reserve specified funds on an account for a given TTL window.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript DTO interface:
 * {@code interface HoldRequest { idempotencyKey: string; accountId: string; amount: Money; ttlSeconds: number; transactionReference: string; }}.</p>
 *
 * <p><strong>Design Note (Caller-provided Idempotency Key):</strong>
 * The idempotency key is passed in rather than generated internally, allowing callers
 * to safely rebuild and retry requests using the exact same idempotency identifier.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Translating to Protobuf {@code payment.bank.v1.HoldRequest} belongs in {@link com.paymentsystem.bank.BankClient}.</p>
 */
public class HoldRequest {

    private final UUID idempotencyKey;
    private final UUID accountId;
    private final Money amount;
    private final long ttlSeconds; // Sent explicitly to govern how long the bank holds funds before auto-releasing
    private final UUID transactionReference; // The Payment System's internal TransactionId

    /**
     * Constructs an immutable HoldRequest.
     *
     * @param idempotencyKey unique key guaranteeing exactly-once hold creation
     * @param accountId the bank account on which funds are to be reserved
     * @param amount the monetary quantity and currency to reserve
     * @param ttlSeconds hold validity lifespan in seconds before automatic bank release
     * @param transactionReference the Payment System's internal transaction ID
     */
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