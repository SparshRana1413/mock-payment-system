package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.TransactionStatus;
import com.paymentsystem.model.BankFailureReason;

/**
 * Response DTO for the Mock Bank's Credit RPC operation.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / DTO layer. Built once from the bank's gRPC reply and never mutated,
 * as a response represents an immutable fact that occurred on the bank's ledger.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript response interface:
 * {@code interface CreditResponse { transactionId: string; status: TransactionStatus; ... }}.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * State transition validation and database updates belong in the service layer.</p>
 */
public class CreditResponse {

    private final UUID transactionId;       // The bank's own internal transaction ID, NOT the Payment System's
    private final TransactionStatus status;
    private final BankFailureReason failureReason; // Present when status is FAILED
    private final UUID accountId;
    private final String bankReferenceNumber;
    private final String errorMessage;       // Present when status is FAILED

    /**
     * Constructs an immutable CreditResponse.
     *
     * @param transactionId the bank's internal transaction ID
     * @param status the outcome status of the credit operation
     * @param failureReason the specific failure reason if the credit failed (null otherwise)
     * @param accountId the destination bank account ID
     * @param bankReferenceNumber external reference string provided by the bank
     * @param errorMessage diagnostic error description (null if successful)
     */
    public CreditResponse(UUID transactionId, TransactionStatus status, BankFailureReason failureReason,
                           UUID accountId, String bankReferenceNumber, String errorMessage) {
        this.transactionId = transactionId;
        this.status = status;
        this.failureReason = failureReason;
        this.accountId = accountId;
        this.bankReferenceNumber = bankReferenceNumber;
        this.errorMessage = errorMessage;
    }

    public UUID getTransactionId() { return transactionId; }
    public TransactionStatus getStatus() { return status; }
    public BankFailureReason getFailureReason() { return failureReason; }
    public UUID getAccountId() { return accountId; }
    public String getBankReferenceNumber() { return bankReferenceNumber; }
    public String getErrorMessage() { return errorMessage; }
}