package com.paymentsystem.model;

/**
 * Enumeration of business and operational failure reasons returned by the Mock Bank.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / Domain layer error model. Categorizes reasons why a banking operation
 * could not be completed, allowing services to decide whether to retry or fail the payment.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a typed error code union in TypeScript:
 * {@code type BankFailureReason = 'INSUFFICIENT_FUNDS' | 'ACCOUNT_LOCKED' | ...}.</p>
 */
public enum BankFailureReason {
    /** Account balance is insufficient to cover the requested hold or transfer. */
    INSUFFICIENT_FUNDS,
    /** Bank account has been locked or suspended by the issuing institution. */
    ACCOUNT_LOCKED,
    /** Specified holdId does not exist on the bank's ledger. */
    HOLD_NOT_FOUND,
    /** Specified hold has already been captured and cannot be re-captured or released. */
    HOLD_ALREADY_CAPTURED,
    /** Specified hold was previously released and cannot be captured. */
    HOLD_ALREADY_RELEASED,
    /** Specified hold exceeded its TTL and expired before capture was attempted. */
    HOLD_EXPIRED,
    /** Deliberate chaos failure injected by the Mock Bank for fault-tolerance testing. */
    CHAOS_INJECTED
}