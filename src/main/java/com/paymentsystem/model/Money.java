package com.paymentsystem.model;

import java.util.Objects;

/**
 * Domain Value Object representing a monetary amount as an integer count of minor units
 * (e.g. paise, cents) plus an ISO-4217 currency code.
 *
 * <p><strong>Architectural Role:</strong>
 * Core Domain / Value Object layer. Adheres strictly to ADR-002 and AGENTS.md rule 2
 * ("Strict Precision for Monetary Values"). Prevents precision loss and floating-point errors
 * across domain services and gRPC boundaries.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to using integer cents in Stripe API payloads, or a value object library
 * like {@code dinero.js} or Martin Fowler's Money pattern in TypeScript.</p>
 *
 * <p>Why not a float/double/BigDecimal?
 * <ul>
 *  <li><strong>float/double:</strong> binary floating point cannot represent decimal fractions
 *    exactly (0.1 + 0.2 != 0.3), which is unacceptable for financial balances.</li>
 *  <li><strong>long (chosen here) over BigInteger:</strong> a {@code long} safely holds amounts up to
 *    ~92 quintillion minor units, which is astronomically more than any realistic transaction
 *    or ledger total will ever need, avoiding object allocation overhead on high-throughput paths.</li>
 *  <li><strong>BigDecimal:</strong> carries scale and rounding footguns (e.g. {@code new BigDecimal("1.0").equals(new BigDecimal("1.00"))}
 *    evaluates to false) and has slower arithmetic compared to integer arithmetic.</li>
 * </ul>
 *
 * <p>This class is immutable: an amount transferred is an immutable historical fact;
 * immutability ensures thread-safety without locks.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Currency exchange rate calculation and conversions belong in a dedicated exchange rate service.</p>
 */
public final class Money {

    private final long amountInMinorUnits;
    private final String currency; // ISO-4217, e.g. "INR", "USD" — always 3 uppercase letters

    /**
     * Constructs an immutable Money instance.
     *
     * @param amountInMinorUnits the non-negative amount in minor currency units (e.g., cents, paise)
     * @param currency 3-letter ISO-4217 uppercase currency code
     * @throws IllegalArgumentException if amount is negative or currency is invalid
     */
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
     * Adds two Money values of the same currency.
     *
     * @param other the other Money instance to add
     * @return a new Money instance representing the sum
     * @throws IllegalArgumentException if currencies differ
     */
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amountInMinorUnits + other.amountInMinorUnits, this.currency);
    }

    /**
     * Subtracts another Money value from this one.
     *
     * @param other the Money instance to subtract
     * @return a new Money instance representing the difference
     * @throws IllegalArgumentException if currencies differ or if the result is negative
     */
    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amountInMinorUnits - other.amountInMinorUnits, this.currency);
    }

    /**
     * Checks if this amount is strictly greater than another Money amount of the same currency.
     *
     * @param other the Money instance to compare against
     * @return true if this amount is strictly greater
     * @throws IllegalArgumentException if currencies differ
     */
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