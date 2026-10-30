package com.paymentsystem.model;

/**
 * Outcome of an individual RPC network call attempt to the external bank.
 *
 * <p><strong>Architectural Role:</strong>
 * Core Domain / Audit trail enum. Used by {@link BankCallAttempt} to record whether a
 * specific gRPC request succeeded or failed at the transport/execution layer.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript string union: {@code type AttemptResult = 'SUCCESS' | 'FAILED'}.</p>
 */
enum AttemptResult {
    /** The bank RPC call executed and returned a successful status. */
    SUCCESS,
    /** The bank RPC call failed due to network error, business rejection, or chaos injection. */
    FAILED
}