package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.Money;

/**
 * Request DTO for the bank's Reverse RPC. Keys off the bank's own
 * transactionId (returned from a prior Capture or Credit call) — NOT a
 * holdId, since by the time you need to reverse something, the hold is
 * long gone; a Reverse undoes a settled movement of money, not a hold.
 */
public class ReverseRequest {

    private final UUID idempotencyKey;
    private final UUID originalTransactionId; // the bank's transactionId from the Capture/Credit being undone
    private final Money amountToReverse;
    private final String reason;

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