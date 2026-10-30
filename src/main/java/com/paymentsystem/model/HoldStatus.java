package com.paymentsystem.model;

/**
 * Represents the persistent lifecycle state of an authorization hold on funds.
 *
 * <p><strong>Architectural Role:</strong>
 * Core Domain enum. A hold is a stateful resource created in two-phase payments
 * (Auth/Capture) that reserves funds on a bank account. This enum models the standing state
 * of that reservation across multiple RPC operations.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript string literal union:
 * {@code type HoldStatus = 'ACTIVE' | 'CAPTURED' | 'RELEASED' | 'EXPIRED'}.</p>
 *
 * <p><strong>Distinction from TransactionStatus:</strong>
 * A Capture RPC call can succeed (the network call worked), resulting in a HoldStatus
 * of {@code CAPTURED} (the hold is settled) while the overall {@link TransactionStatus} transitions
 * from {@code HELD} to {@code CAPTURED}.</p>
 */
public enum HoldStatus {
    /** Funds are reserved and awaiting capture or release. */
    ACTIVE,
    /** Funds have been debited; the hold is permanently settled. */
    CAPTURED,
    /** Hold was cancelled before capture; funds returned to available balance. */
    RELEASED,
    /** Hold's time-to-live elapsed before capture; funds automatically released by bank. */
    EXPIRED
}