package com.paymentsystem.dto;

import java.util.UUID;

/**
 * Request DTO for the Mock Bank's GetBalance RPC operation.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / DTO layer. Encapsulates query parameters to fetch real-time cleared,
 * held, and available balances from the Mock Bank service.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript query params DTO:
 * {@code interface GetBalanceRequest { accountId: string; }}.</p>
 *
 * <p><strong>Design Note (No Idempotency Key):</strong>
 * Because GetBalance is a pure read operation with no side effects or ledger mutations,
 * it is inherently idempotent and does not require an idempotency key.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * User authorization checks (verifying the caller owns this bank account) belong in the controller/service layer.</p>
 */
public class GetBalanceRequest {

    private final UUID accountId;

    /**
     * Constructs a GetBalanceRequest for a specific bank account.
     *
     * @param accountId the unique identifier of the bank account to query
     */
    public GetBalanceRequest(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getAccountId() { return accountId; }
}