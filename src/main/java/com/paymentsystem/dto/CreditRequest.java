package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.Money;

/**
 * Request DTO for the Mock Bank's Credit RPC operation.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / DTO layer. Encapsulates funds transfer instructions to credit money
 * directly into a recipient's bank account.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript DTO interface:
 * {@code interface CreditRequest { idempotencyKey: string; destinationAccountId: string; amount: Money; transactionReference: string; }}.</p>
 *
 * <p><strong>Ownership of Idempotency Key:</strong>
 * This class does NOT generate its own key. The idempotency key represents "this specific attempt
 * at this specific bank operation," and that decision belongs to the service orchestrating retries.
 * If this class generated its own key in the constructor, retrying with a new request object would
 * silently bypass deduplication on the bank's side.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Mapping to {@code payment.bank.v1.CreditRequest} Protobuf and gRPC stub calls belong in
 * {@link com.paymentsystem.bank.BankClient}.</p>
 */
public class CreditRequest {

    private final UUID idempotencyKey;
    private final UUID destinationAccountId;
    private final Money amount;
    private final UUID transactionReference; // Payment System's TransactionId, not the bank's

    /**
     * Constructs a CreditRequest.
     *
     * @param idempotencyKey unique key guaranteeing exactly-once crediting execution
     * @param destinationAccountId the target bank account receiving funds
     * @param amount the exact monetary value and currency to credit
     * @param transactionReference the Payment System's internal transaction ID
     */
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