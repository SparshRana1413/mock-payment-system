package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.TransactionStatus;
import com.paymentsystem.model.BankFailureReason;

public class ReverseResponse {

    private final UUID originalTransactionId;
    private final UUID reversalTransactionId; // new bank transaction representing the reversal itself
    private final TransactionStatus status;
    private final BankFailureReason failureReason;
    private final UUID accountId;
    private final String bankReferenceNumber;
    private final String errorMessage;

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