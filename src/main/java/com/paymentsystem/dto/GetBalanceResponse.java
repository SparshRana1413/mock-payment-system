package com.paymentsystem.dto;

import java.util.UUID;
import com.paymentsystem.model.Money;

public class GetBalanceResponse {

    private final UUID accountId;
    private final Money held;      // total currently reserved by active holds
    private final Money available; // balance - held; what's actually spendable
    private final Money balance;   // total recorded on the bank's ledger

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