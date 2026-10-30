package com.paymentsystem.model;

/**
 * Represents the lifecycle status of a payment transaction within the Payment System.
 *
 * <p><strong>Architectural Role:</strong>
 * Core Domain enum. Reflects the operational state transitions of a {@link Transaction}
 * from initiation to final settlement or failure, aligning with {@code payment.bank.v1.TransactionStatus}
 * in the Protobuf specification.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript enum or string literal union:
 * {@code type TransactionStatus = 'PENDING' | 'HELD' | 'CAPTURED' | 'COMPLETED' | 'REVERSED' | 'FAILED'}.</p>
 *
 * <p><strong>Domain State Distinctions:</strong>
 * <ul>
 *   <li>{@code TransactionStatus} models the state of the overall payment transaction.</li>
 *   <li>{@link HoldStatus} models the standing state of a reserved fund hold.</li>
 *   <li>{@link AttemptResult} models the discrete network outcome (success/failure) of an individual RPC attempt.</li>
 * </ul></p>
 */
public enum TransactionStatus {
    /** Transaction created and initialized, awaiting bank processing. */
    PENDING,
    /** Funds have been authorized/held on the payer's bank account. */
    HELD,
    /** Held funds have been captured by the bank. */
    CAPTURED,
    /** Funds have been successfully settled and credited to the payee. */
    COMPLETED,
    /** A previously captured or credited transaction has been undone. */
    REVERSED,
    /** Transaction aborted or failed at any step in the payment pipeline. */
    FAILED
}