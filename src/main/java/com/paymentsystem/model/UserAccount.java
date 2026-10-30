package com.paymentsystem.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing an application user and their associated linked bank account.
 *
 * <p><strong>Architectural Role:</strong>
 * Core Domain layer. Represents an in-memory domain concept of a user account bound to a
 * {@link BankAccount}. Note that this is distinct from the JPA-persisted {@link User} entity
 * which maps directly to the {@code users} table in PostgreSQL.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript domain aggregate root or model class representing a user and
 * their bank relationship in domain-driven design (DDD).</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Database persistence in PostgreSQL is handled by {@link User} via {@link com.paymentsystem.repository.UserRepository}.</p>
 */
public class UserAccount {
    private final UUID id;
    private String username; // username can be changed if need be
    private final String passwordHash;
    private final Instant createdAt;
    private BankAccount bankAccount; // bankAccount can be changed if the user wants to switch banks

    /**
     * Constructs a new UserAccount domain instance.
     *
     * @param username the account username
     * @param passwordHash the hashed password string
     * @param bankAccount the linked bank account reference
     */
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