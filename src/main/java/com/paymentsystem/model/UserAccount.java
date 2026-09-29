package com.paymentsystem.model;

import java.time.Instant;
import java.util.UUID;

public class UserAccount {
    private final UUID id;
    private String username; // username can be changed if need be
    private final String passwordHash;
    private final Instant createdAt;
    private BankAccount bankAccount; // bankAccount can be changed if the user wants to switch banks

    public UserAccount(String username, String passwordHash, BankAccount bankAccount) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
        this.bankAccount = bankAccount;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BankAccount getBankAccount() {
        return bankAccount;
    }
}