package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.Money;
import com.paymentsystem.model.TransactionStatus;
import com.paymentsystem.model.BankFailureReason;
import com.paymentsystem.model.HoldStatus;

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