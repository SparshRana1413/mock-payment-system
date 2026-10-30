package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.Money;
import com.paymentsystem.model.TransactionStatus;
import com.paymentsystem.model.BankFailureReason;
import com.paymentsystem.model.HoldStatus;

/**
 * Response DTO conveying the result of a Release RPC operation from the Mock Bank.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / DTO layer. Confirms that reserved funds have been returned to the available balance
 * and reports the updated hold status ({@link HoldStatus#RELEASED}).</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript response interface:
 * {@code interface ReleaseResponse { holdId: string; holdStatus: HoldStatus; ... }}.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Updating the local transaction status to FAILED or CANCELLED belongs in the service layer.</p>
 */
public class ReleaseResponse {

    private final UUID holdId;
    private final UUID transactionId;
    private final TransactionStatus status;
    private final BankFailureReason failureReason;
    private final HoldStatus holdStatus;
    private final UUID accountId;
    private final Money held;
    private final Money balance;
    private final String errorMessage;

    /**
     * Constructs an immutable ReleaseResponse.
     *
     * @param holdId the ID of the released hold
     * @param transactionId the bank's internal transaction ID
     * @param status the outcome status of the release operation
     * @param failureReason failure diagnostic if the release failed (null otherwise)
     * @param holdStatus the new standing state of the hold (typically RELEASED)
     * @param accountId the bank account ID
     * @param held remaining funds held after this release
     * @param balance current ledger balance
     * @param errorMessage human-readable error description (null if successful)
     */
    public ReleaseResponse(UUID holdId, UUID transactionId, TransactionStatus status, BankFailureReason failureReason,
                            HoldStatus holdStatus, UUID accountId, Money held, Money balance, String errorMessage) {
        this.holdId = holdId;
        this.transactionId = transactionId;
        this.status = status;
        this.failureReason = failureReason;
        this.holdStatus = holdStatus;
        this.accountId = accountId;
        this.held = held;
        this.balance = balance;
        this.errorMessage = errorMessage;
    }

    public UUID getHoldId() { return holdId; }
    public UUID getTransactionId() { return transactionId; }
    public TransactionStatus getStatus() { return status; }
    public BankFailureReason getFailureReason() { return failureReason; }
    public HoldStatus getHoldStatus() { return holdStatus; }
    public UUID getAccountId() { return accountId; }
    public Money getHeld() { return held; }
    public Money getBalance() { return balance; }
    public String getErrorMessage() { return errorMessage; }
}