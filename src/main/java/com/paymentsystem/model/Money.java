package com.paymentsystem.model;

import java.util.Objects;

/**
 * Represents a monetary amount as an integer count of minor units
 * (e.g. paise, cents) plus an ISO-4217 currency code.
 *
 * Why not a float/double/BigDecimal?
 *  - float/double: binary floating point cannot represent decimal fractions
 *    exactly (0.1 + 0.2 != 0.3), which is unacceptable for money.
 *  - long (chosen here) over BigInteger: a long safely holds amounts up to
 *    ~92 quintillion minor units, which is astronomically more than any
 *    realistic single transaction or even ledger total in this system will
 *    ever need. BigInteger is for arbitrary precision, but that's solving a
 *    problem we don't have and costs boxing/allocation overhead on every
 *    arithmetic operation, which matters if you're targeting thousands of
 *    transactions per second.
 *  - BigDecimal is the *other* legitimate choice for money, since it's
 *    exact and human-readable (100.50 instead of 10050), but it carries
 *    scale/rounding-mode footguns (BigDecimal("1.0").equals(BigDecimal("1.00"))
 *    is false) and is slower. Integer minor units is the more common
 *    convention in real payment systems (this is literally what Stripe's
 *    API does) and it's what I'd recommend here.
 *
 * This class is immutable: an amount transferred is a fact recorded at a
 * point in time, so mutation makes no sense and immutability makes it safe
 * to pass around across threads without synchronization.
 */
public final class Money {

    private final long amountInMinorUnits;
    private final String currency; // ISO-4217, e.g. "INR", "USD" — always 3 uppercase letters

    public Money(long amountInMinorUnits, String currency) {
        if (amountInMinorUnits < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative: " + amountInMinorUnits);
        }
        this.currency = validateCurrency(currency);
        this.amountInMinorUnits = amountInMinorUnits;
    }

    private static String validateCurrency(String currency) {
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a 3-letter ISO-4217 code, got: " + currency);
        }
        return currency;
    }

    public long getAmountInMinorUnits() {
        return amountInMinorUnits;
    }

    public String getCurrency() {
        return currency;
    }

    /**
     * Adds two Money values. Throws if currencies differ — we do NOT
     * silently convert currencies; that's a business decision this class
     * has no business making.
     */
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amountInMinorUnits + other.amountInMinorUnits, this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amountInMinorUnits - other.amountInMinorUnits, this.currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return this.amountInMinorUnits > other.amountInMinorUnits;
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Currency mismatch: " + this.currency + " vs " + other.currency);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money)) return false;
        Money money = (Money) o;
        return amountInMinorUnits == money.amountInMinorUnits && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountInMinorUnits, currency);
    }

    @Override
    public String toString() {
        return amountInMinorUnits + " " + currency;
    }
}