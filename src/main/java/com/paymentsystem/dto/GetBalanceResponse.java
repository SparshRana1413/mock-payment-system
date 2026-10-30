package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.Money;

/**
 * Response DTO conveying account balance breakdown from the Mock Bank.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / DTO layer. Represents the live financial standing of a bank account,
 * distinguishing total ledger balance from currently reserved holds and spendable funds.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript response interface:
 * {@code interface GetBalanceResponse { accountId: string; held: Money; available: Money; balance: Money; }}.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Transforming to client-facing HTTP payloads belongs in the controller layer.</p>
 */
public class GetBalanceResponse {

    private final UUID accountId;
    private final Money held;      // Total funds currently reserved by active authorization holds
    private final Money available; // Funds immediately spendable: available = balance - held
    private final Money balance;   // Total settled funds recorded on the bank's core ledger

    /**
     * Constructs an immutable GetBalanceResponse.
     *
     * @param accountId the queried bank account identifier
     * @param held amount currently reserved under active holds
     * @param available spendable amount (balance - held)
     * @param balance total cleared ledger balance
     */
    public GetBalanceResponse(UUID accountId, Money held, Money available, Money balance) {
        this.accountId = accountId;
        this.held = held;
        this.available = available;
        this.balance = balance;
    }

    public UUID getAccountId() { return accountId; }
    public Money getHeld() { return held; }
    public Money getAvailable() { return available; }
    public Money getBalance() { return balance; }
}