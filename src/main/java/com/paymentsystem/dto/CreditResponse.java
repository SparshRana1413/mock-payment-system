package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.TransactionStatus;
import com.paymentsystem.model.BankFailureReason;

/**
 * Response DTO for the bank's Credit RPC. Built once, from the bank's
 * reply, and never mutated afterward — a response represents a fact that
 * already happened, so no setters.
 */
public class CreditResponse {

    private final UUID transactionId;       // the bank's own transaction ID, NOT your TransactionId
    private final TransactionStatus status;
    private final BankFailureReason failureReason; // null when status == SUCCEEDED
    private final UUID accountId;
    private final String bankReferenceNumber;
    private final String errorMessage;       // null when status == SUCCEEDED

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