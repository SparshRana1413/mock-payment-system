package com.paymentsystem.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.paymentsystem.model.Transaction;

/**
 * Public REST API Response DTO representing the state of a payment transaction.
 *
 * <p><strong>Architectural Role:</strong>
 * Presentation / DTO layer. Projects internal domain entities ({@link Transaction})
 * into a safe, client-facing contract without exposing private implementation details
 * like retry attempts or internal locks.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript response model or mapping function
 * (e.g., {@code toTransactionDto(tx: Transaction): TransactionResponseDto}).</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Database querying and state orchestration belong in the service layer.</p>
 */
public class TransactionResponse {
    private final UUID transactionId;
    private final String fromAccount;
    private final String toAccount;
    private final BigDecimal amount;
    private final String status;
    private final Instant timestamp;

    /**
     * Constructs a TransactionResponse DTO.
     *
     * @param transactionId the unique transaction identifier
     * @param fromAccount source bank account identifier
     * @param toAccount destination bank account identifier
     * @param amount the transferred monetary quantity
     * @param status the string representation of the transaction status
     * @param timestamp completion time if available, otherwise creation time
     */
    public TransactionResponse(UUID transactionId, String fromAccount, String toAccount,
                                    BigDecimal amount, String status, Instant timestamp) {
        this.transactionId = transactionId;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.status = status;
        this.timestamp = timestamp;
    }

    /**
     * Static factory method mapping an internal domain {@link Transaction} entity into a client DTO.
     *
     * <p>Prefers {@code completedAt} as the primary timestamp if available, falling back
     * to {@code createdAt} for pending or in-progress transactions.</p>
     *
     * @param t the domain transaction to map
     * @return a populated {@link TransactionResponse}
     */
    public static TransactionResponse fromEntity(Transaction t) {
        return new TransactionResponse(
            t.getTransactionId(), t.getFromAccount(), t.getToAccount(),
            t.getAmount(), t.getStatus().name(),
            t.getCompletedAt() != null ? t.getCompletedAt() : t.getCreatedAt()
        );
    }

    public UUID getTransactionID(){ return transactionId; }
    public String getFromAccount(){ return fromAccount; }
    public String getToAccount(){ return toAccount; }
    public BigDecimal getAmount(){ return amount; }
    public String getStatus(){ return status; }
    public Instant getTimestamp(){ return timestamp; }
}
