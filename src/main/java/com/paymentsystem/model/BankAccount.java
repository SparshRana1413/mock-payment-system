package com.paymentsystem.model;

import java.util.UUID;
import java.util.Objects;

public class BankAccount {
    private final UUID id;
    private final String currency;
    private final String holderName;
    private final String IFSCcode;
    private final String bankName;

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