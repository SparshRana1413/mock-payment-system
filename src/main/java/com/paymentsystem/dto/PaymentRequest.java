package com.paymentsystem.dto;

import com.paymentsystem.model.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * Inbound Request DTO capturing two-leg payment instructions.
 *
 * <p><strong>Architectural Role:</strong>
 * Presentation / DTO layer. Defines the contract for initiating a financial payment
 * from a source bank account to a destination bank account.</p>
 */
public class PaymentRequest {

    @NotBlank(message = "Idempotency key must not be blank")
    private final String idempotencyKey;

    @NotBlank(message = "From account must not be blank")
    private final String fromAccount;

    @NotBlank(message = "To account must not be blank")
    private final String toAccount;

    @NotNull(message = "Amount must not be null")
    @Positive(message = "Amount must be strictly positive")
    private final BigDecimal amount;

    @NotBlank(message = "Currency must not be blank")
    private final String currency;

    /**
     * Constructs a PaymentRequest with explicit string identifiers and BigDecimal amount.
     *
     * @param idempotencyKey unique key guaranteeing exactly-once processing
     * @param fromAccount source bank account identifier
     * @param toAccount destination bank account identifier
     * @param amount monetary value to transfer
     * @param currency 3-letter ISO-4217 currency code (defaults to "USD" if null)
     */
    public PaymentRequest(String idempotencyKey, String fromAccount, String toAccount,
                          BigDecimal amount, String currency) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be null or blank");
        }
        if (fromAccount == null || fromAccount.isBlank()) {
            throw new IllegalArgumentException("fromAccount must not be null or blank");
        }
        if (toAccount == null || toAccount.isBlank()) {
            throw new IllegalArgumentException("toAccount must not be null or blank");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be non-null and strictly positive");
        }
        this.idempotencyKey = idempotencyKey.trim();
        this.fromAccount = fromAccount.trim();
        this.toAccount = toAccount.trim();
        this.amount = amount;
        this.currency = (currency != null && !currency.isBlank()) ? currency.trim().toUpperCase() : "USD";
    }

    /**
     * Convenience constructor accepting UUID parameters.
     */
    public PaymentRequest(UUID idempotencyKey, UUID fromAccount, UUID toAccount,
                          BigDecimal amount, String currency) {
        this(
                Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null").toString(),
                Objects.requireNonNull(fromAccount, "fromAccount must not be null").toString(),
                Objects.requireNonNull(toAccount, "toAccount must not be null").toString(),
                amount,
                currency
        );
    }

    /**
     * Convenience constructor accepting domain {@link Money}.
     */
    public PaymentRequest(String idempotencyKey, String fromAccount, String toAccount, Money money) {
        this(
                idempotencyKey,
                fromAccount,
                toAccount,
                BigDecimal.valueOf(Objects.requireNonNull(money, "money must not be null").getAmountInMinorUnits())
                        .movePointLeft(2),
                money.getCurrency()
        );
    }

    /**
     * Convenience constructor accepting UUIDs and domain {@link Money}.
     */
    public PaymentRequest(UUID idempotencyKey, UUID fromAccount, UUID toAccount, Money money) {
        this(
                Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null").toString(),
                Objects.requireNonNull(fromAccount, "fromAccount must not be null").toString(),
                Objects.requireNonNull(toAccount, "toAccount must not be null").toString(),
                money
        );
    }

    private PaymentRequest(Builder b) {
        this(b.idempotencyKey, b.fromAccount, b.toAccount, b.amount, b.currency);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String idempotencyKey;
        private String fromAccount;
        private String toAccount;
        private BigDecimal amount;
        private String currency = "USD";

        public Builder idempotencyKey(String v) { this.idempotencyKey = v; return this; }
        public Builder idempotencyKey(UUID v) { this.idempotencyKey = (v != null ? v.toString() : null); return this; }
        public Builder fromAccount(String v) { this.fromAccount = v; return this; }
        public Builder fromAccount(UUID v) { this.fromAccount = (v != null ? v.toString() : null); return this; }
        public Builder toAccount(String v) { this.toAccount = v; return this; }
        public Builder toAccount(UUID v) { this.toAccount = (v != null ? v.toString() : null); return this; }
        public Builder amount(BigDecimal v) { this.amount = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder money(Money money) {
            if (money != null) {
                this.amount = BigDecimal.valueOf(money.getAmountInMinorUnits()).movePointLeft(2);
                this.currency = money.getCurrency();
            }
            return this;
        }

        public PaymentRequest build() {
            return new PaymentRequest(this);
        }
    }

    /**
     * Converts this request's amount and currency into a domain {@link Money} instance in minor units.
     */
    public Money toMoney() {
        long minorUnits = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new Money(minorUnits, currency);
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getFromAccount() { return fromAccount; }
    public String getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
}

