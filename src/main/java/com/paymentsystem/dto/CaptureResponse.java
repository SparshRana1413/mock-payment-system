package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.Money;
import com.paymentsystem.model.TransactionStatus;
import com.paymentsystem.model.BankFailureReason;
import com.paymentsystem.model.HoldStatus;

/**
 * Response DTO conveying the result of a Capture RPC operation from the Mock Bank.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / DTO layer. Carries the bank's response details (updated ledger balance,
 * captured amount, hold status, and any error diagnostics) back to the payment service.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript interface for an RPC response:
 * {@code interface CaptureResponse { holdId: string; status: TransactionStatus; ... }}.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Persisting the updated transaction state in PostgreSQL belongs in the service layer.</p>
 */
public class CaptureResponse {

    private final UUID holdId;
    private final UUID transactionId;
    private final TransactionStatus status;
    private final BankFailureReason failureReason;
    private final HoldStatus holdStatus;
    private final UUID accountId;
    private final Money balance;
    private final Money capturedAmount;
    private final String errorMessage;

    /**
     * Constructs an immutable CaptureResponse.
     *
     * @param holdId the ID of the captured hold
     * @param transactionId the bank's internal transaction ID
     * @param status the outcome status of the capture
     * @param failureReason the specific failure reason if the capture failed (null if successful)
     * @param holdStatus the new standing status of the hold (typically CAPTURED)
     * @param accountId the bank account ID that was debited
     * @param balance the updated ledger balance after capture
     * @param capturedAmount the monetary amount that was debited
     * @param errorMessage human-readable error description (null if successful)
     */
    public CaptureResponse(UUID holdId, UUID transactionId, TransactionStatus status, BankFailureReason failureReason,
                            HoldStatus holdStatus, UUID accountId, Money balance, Money capturedAmount, String errorMessage) {
        this.holdId = holdId;
        this.transactionId = transactionId;
        this.status = status;
        this.failureReason = failureReason;
        this.holdStatus = holdStatus;
        this.accountId = accountId;
        this.balance = balance;
        this.capturedAmount = capturedAmount;
        this.errorMessage = errorMessage;
    }

    public UUID getHoldId() { return holdId; }
    public UUID getTransactionId() { return transactionId; }
    public TransactionStatus getStatus() { return status; }
    public BankFailureReason getFailureReason() { return failureReason; }
    public HoldStatus getHoldStatus() { return holdStatus; }
    public UUID getAccountId() { return accountId; }
    public Money getBalance() { return balance; }
    public Money getCapturedAmount() { return capturedAmount; }
    public String getErrorMessage() { return errorMessage; }
}