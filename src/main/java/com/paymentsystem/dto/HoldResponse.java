package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.Money;
import com.paymentsystem.model.TransactionStatus;
import com.paymentsystem.model.BankFailureReason;
import com.paymentsystem.model.HoldStatus;

/**
 * Response DTO for the bank's Hold RPC. Carries all three status enums
 * because Hold both is an RPC call (status/failureReason) AND creates a
 * standing resource (holdStatus).
 */
public class HoldResponse {

    private final UUID holdId;
    private final UUID transactionId;
    private final TransactionStatus status;
    private final BankFailureReason failureReason; // null when status == SUCCEEDED
    private final HoldStatus holdStatus;           // null when status == FAILED
    private final UUID accountId;
    private final Money balance;
    private final Money held;
    private final String errorMessage;

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