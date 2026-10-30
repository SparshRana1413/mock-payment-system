package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.TransactionStatus;
import com.paymentsystem.model.BankFailureReason;

/**
 * Response DTO conveying the result of a Reverse RPC operation from the Mock Bank.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / DTO layer. Reports whether the reversal succeeded and returns the new bank
 * transaction ID generated to represent the refund ledger entry.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript response interface:
 * {@code interface ReverseResponse { originalTransactionId: string; reversalTransactionId: string; ... }}.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Updating the payment system's internal transaction status to REVERSED belongs in the service layer.</p>
 */
public class ReverseResponse {

    private final UUID originalTransactionId;
    private final UUID reversalTransactionId; // New bank transaction representing the reversal itself
    private final TransactionStatus status;
    private final BankFailureReason failureReason;
    private final UUID accountId;
    private final String bankReferenceNumber;
    private final String errorMessage;

    /**
     * Constructs an immutable ReverseResponse.
     *
     * @param originalTransactionId the ID of the transaction being reversed
     * @param reversalTransactionId the newly generated bank transaction ID for the reversal entry
     * @param status the outcome status of the reversal
     * @param failureReason diagnostic code if the reversal failed (null otherwise)
     * @param accountId the account affected by the reversal
     * @param bankReferenceNumber bank reference string
     * @param errorMessage human-readable error description (null if successful)
     */
    public ReverseResponse(UUID originalTransactionId, UUID reversalTransactionId, TransactionStatus status,
                            BankFailureReason failureReason, UUID accountId, String bankReferenceNumber, String errorMessage) {
        this.originalTransactionId = originalTransactionId;
        this.reversalTransactionId = reversalTransactionId;
        this.status = status;
        this.failureReason = failureReason;
        this.accountId = accountId;
        this.bankReferenceNumber = bankReferenceNumber;
        this.errorMessage = errorMessage;
    }

    public UUID getOriginalTransactionId() { return originalTransactionId; }
    public UUID getReversalTransactionId() { return reversalTransactionId; }
    public TransactionStatus getStatus() { return status; }
    public BankFailureReason getFailureReason() { return failureReason; }
    public UUID getAccountId() { return accountId; }
    public String getBankReferenceNumber() { return bankReferenceNumber; }
    public String getErrorMessage() { return errorMessage; }
}