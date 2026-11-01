package com.paymentsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA Entity representing a user's linked bank account in PostgreSQL.
 *
 * <p><strong>Architectural Role:</strong>
 * Domain Model / Persistence Entity layer. Maps directly to the {@code linked_bank_accounts} table
 * created by Flyway migration {@code V2__create_linked_bank_accounts.sql}.
 * Represents the association between an application {@link User} and their external bank account details.</p>
 */
@Entity
@Table(
    name = "linked_bank_accounts",
    indexes = {
        @Index(name = "idx_linked_bank_accounts_user_id", columnList = "user_id")
    }
)
public class LinkedBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "bank_account_id", nullable = false, length = 255)
    private String bankAccountId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "balance", nullable = false)
    private Long balance = 0L;

    @Column(name = "held", nullable = false)
    private Long held = 0L;

    @Column(name = "bank_name", nullable = false, length = 255)
    private String bankName;

    @Column(name = "holder_name", nullable = false, length = 255)
    private String holderName;

    /**
     * Default no-argument constructor required by JPA/Hibernate proxy instantiation.
     */
    protected LinkedBankAccount() {}

    /**
     * Constructs a LinkedBankAccount entity with initial default balance and hold values.
     *
     * @param user the owning application user
     * @param bankAccountId external bank account identifier
     * @param currency 3-letter currency code (e.g. "USD", "INR")
     * @param bankName name of the issuing bank
     * @param holderName name of the account holder
     */
    public LinkedBankAccount(User user, String bankAccountId, String currency, String bankName, String holderName) {
        this(user, bankAccountId, currency, 0L, 0L, bankName, holderName);
    }

    /**
     * Full constructor for LinkedBankAccount entity.
     *
     * @param user the owning application user
     * @param bankAccountId external bank account identifier
     * @param currency 3-letter currency code
     * @param balance current cleared balance in minor units
     * @param held currently reserved amount in minor units
     * @param bankName name of the issuing bank
     * @param holderName name of the account holder
     */
    public LinkedBankAccount(User user, String bankAccountId, String currency, Long balance, Long held,
                             String bankName, String holderName) {
        this.user = user;
        this.bankAccountId = bankAccountId;
        this.currency = currency;
        this.balance = (balance != null ? balance : 0L);
        this.held = (held != null ? held : 0L);
        this.bankName = bankName;
        this.holderName = holderName;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (balance == null) {
            balance = 0L;
        }
        if (held == null) {
            held = 0L;
        }
    }

    // Getters and Setters
    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Long getUserId() { return user != null ? user.getId() : null; }

    public String getBankAccountId() { return bankAccountId; }
    public void setBankAccountId(String bankAccountId) { this.bankAccountId = bankAccountId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public Long getBalance() { return balance; }
    public void setBalance(Long balance) { this.balance = balance; }

    public Long getHeld() { return held; }
    public void setHeld(Long held) { this.held = held; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
}

