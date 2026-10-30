package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.Money;
import com.paymentsystem.model.TransactionStatus;
import com.paymentsystem.model.BankFailureReason;
import com.paymentsystem.model.HoldStatus;

/**
 * Response DTO conveying the result of a Hold RPC operation from the Mock Bank.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / DTO layer. Carries the identifier of the newly created fund hold ({@code holdId}),
 * the bank transaction identifier, and updated balances.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript response interface:
 * {@code interface HoldResponse { holdId: string; transactionId: string; status: TransactionStatus; ... }}.</p>
 *
 * <p><strong>Design Note (Status Enums):</strong>
 * Carries {@link TransactionStatus} for the overall transaction progression,
 * {@link BankFailureReason} if the call was rejected, and {@link HoldStatus} for the standing state of the hold.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Recording the {@code holdId} against local transaction entities belongs in the service layer.</p>
 */
public class HoldResponse {

    private final UUID holdId;
    private final UUID transactionId;
    private final TransactionStatus status;
    private final BankFailureReason failureReason; // Present when status is FAILED
    private final HoldStatus holdStatus;           // Present when hold is created (e.g. ACTIVE)
    private final UUID accountId;
    private final Money balance;
    private final Money held;
    private final String errorMessage;

    /**
     * Constructs an immutable HoldResponse.
     *
     * @param holdId the bank-generated unique hold identifier
     * @param transactionId the bank's internal transaction ID
     * @param status the outcome status of the hold operation
     * @param failureReason the specific failure reason if the hold failed (null otherwise)
     * @param holdStatus the state of the created hold (ACTIVE on success, null on failure)
     * @param accountId the account on which funds were held
     * @param balance current cleared ledger balance
     * @param held total amount reserved under active holds including this one
     * @param errorMessage human-readable error description (null if successful)
     */
    public HoldResponse(UUID holdId, UUID transactionId, TransactionStatus status, BankFailureReason failureReason,
                         HoldStatus holdStatus, UUID accountId, Money balance, Money held, String errorMessage) {
        this.holdId = holdId;
        this.transactionId = transactionId;
        this.status = status;
        this.failureReason = failureReason;
        this.holdStatus = holdStatus;
        this.accountId = accountId;
        this.balance = balance;
        this.held = held;
        this.errorMessage = errorMessage;
    }

    public UUID getHoldId() { return holdId; }
    public UUID getTransactionId() { return transactionId; }
    public TransactionStatus getStatus() { return status; }
    public BankFailureReason getFailureReason() { return failureReason; }
    public HoldStatus getHoldStatus() { return holdStatus; }
    public UUID getAccountId() { return accountId; }
    public Money getBalance() { return balance; }
    public Money getHeld() { return held; }
    public String getErrorMessage() { return errorMessage; }
}