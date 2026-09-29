package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.Money;

/**
 * Request DTO for the bank's Credit RPC.
 *
 * Ownership of the idempotency key: this class does NOT generate one.
 * The idempotency key represents "this specific attempt at this specific
 * bank operation," and that decision belongs to whichever service is
 * orchestrating retries (your future CreditService / saga step), not to
 * this DTO. If this class generated its own key in its constructor, then
 * every retry that rebuilds a CreditRequest would silently get a brand-new
 * key and defeat deduplication on the bank's side — which is the whole
 * point of having the key in the first place.
 *
 * So: the caller decides the key (typically derived from your own
 * TransactionId, e.g. "credit-step-of-<transactionId>"), and passes it in.
 */
public class CreditRequest {

    private final UUID idempotencyKey;
    private final UUID destinationAccountId;
    private final Money amount;
    private final UUID transactionReference; // your Payment System's TransactionId, not the bank's

    public CreditRequest(UUID idempotencyKey, UUID destinationAccountId, Money amount, UUID transactionReference) {
        this.idempotencyKey = idempotencyKey;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.transactionReference = transactionReference;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public Money getAmount() {
        return amount;
    }

    public UUID getTransactionReference() {
        return transactionReference;
    }
}