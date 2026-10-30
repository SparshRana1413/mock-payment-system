package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.Money;

/**
 * Request DTO for the Mock Bank's Reverse RPC operation.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / DTO layer. Initiates a reversal/refund of settled funds against a prior
 * bank transaction (from a previously successful Capture or Credit).</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript interface:
 * {@code interface ReverseRequest { idempotencyKey: string; originalTransactionId: string; amountToReverse: Money; reason: string; }}.</p>
 *
 * <p><strong>Design Note (Transaction vs Hold):</strong>
 * Keys off the bank's internal {@code originalTransactionId} rather than a {@code holdId},
 * because a Reverse operation undoes an already settled movement of money after the hold has expired or been consumed.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Verification that the original transaction is reversible belongs in the service layer.</p>
 */
public class ReverseRequest {

    private final UUID idempotencyKey;
    private final UUID originalTransactionId; // The bank's transactionId from the Capture or Credit being undone
    private final Money amountToReverse;
    private final String reason;

    /**
     * Constructs an immutable ReverseRequest.
     *
     * @param idempotencyKey unique key guaranteeing exactly-once reversal execution
     * @param originalTransactionId the bank's internal transaction ID of the original operation
     * @param amountToReverse the monetary amount to be reversed
     * @param reason business reason for the reversal
     */
    public ReverseRequest(UUID idempotencyKey, UUID originalTransactionId, Money amountToReverse, String reason) {
        this.idempotencyKey = idempotencyKey;
        this.originalTransactionId = originalTransactionId;
        this.amountToReverse = amountToReverse;
        this.reason = reason;
    }

    public UUID getIdempotencyKey() { return idempotencyKey; }
    public UUID getOriginalTransactionId() { return originalTransactionId; }
    public Money getAmountToReverse() { return amountToReverse; }
    public String getReason() { return reason; }
}