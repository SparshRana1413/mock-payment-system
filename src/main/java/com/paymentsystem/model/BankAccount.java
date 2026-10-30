package com.paymentsystem.model;

import java.util.UUID;
import java.util.Objects;

/**
 * Domain model representing a user's linked bank account and routing information.
 *
 * <p><strong>Architectural Role:</strong>
 * Core Domain layer. Holds banking metadata (bank name, IFSC code, account holder, currency)
 * required to identify and route payment transactions to the external banking service.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript domain class or interface representing a bank account entity.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Bank ledger balances and actual balance mutations are owned exclusively by the external
 * Mock Bank service per ADR-001; this model represents the local link and routing details.</p>
 */
public class BankAccount {
    private final UUID id;
    private final String currency;
    private final String holderName;
    private final String IFSCcode;
    private final String bankName;

    /**
     * Constructs a new BankAccount domain instance with a generated UUID identifier.
     *
     * @param currency 3-letter currency code (e.g., "INR", "USD")
     * @param holderName the name of the bank account holder
     * @param IFSCcode the bank branch routing identifier (e.g. Indian Financial System Code)
     * @param bankName the issuing financial institution name
     */
    public BankAccount(String currency, String holderName, String IFSCcode, String bankName) {
        this.id = UUID.randomUUID();
        this.currency = currency;
        this.holderName = holderName;
        this.IFSCcode = IFSCcode;
        this.bankName = bankName;
    }

    public UUID getId() {
        return id;
    }

    public String getCurrency() {
        return currency;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getIFSCcode() {
        return IFSCcode;
    }

    public String getBankName() {
        return bankName;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof BankAccount other)) {
            return false;
        }

        return Objects.equals(this.id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}