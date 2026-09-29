package com.paymentsystem.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.paymentsystem.model.Transaction;

public class TransactionResponse {
    private final UUID transactionId;
    private final String fromAccount;
    private final String toAccount;
    private final BigDecimal amount;
    private final String status;
    private final Instant timestamp;

    public TransactionResponse(UUID transactionId, String fromAccount, String toAccount,
                                    BigDecimal amount, String status, Instant timestamp) {
        this.transactionId = transactionId;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.status = status;
        this.timestamp = timestamp;
    }

    public static TransactionResponse fromEntity(Transaction t) {
        return new TransactionResponse(
            t.getTransactionId(), t.getFromAccount(), t.getToAccount(),
            t.getAmount(), t.getStatus().name(),
            t.getCompletedAt() != null ? t.getCompletedAt() : t.getCreatedAt()
        );
    }

    public UUID getTransactionID(){ return transactionId; };
    public String getFromAccount(){ return fromAccount; };
    public String getToAccount(){ return toAccount; };
    public BigDecimal getAmount(){ return amount; };
    public String getStatus(){ return status; };
    public Instant getTimestamp(){ return timestamp; };
}
