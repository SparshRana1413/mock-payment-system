package com.paymentsystem.model;

/**
 * The standing lifecycle state of a Hold, as a resource that persists
 * across multiple RPC calls. Distinct from {@link TransactionStatus}:
 * a Capture call can SUCCEED (the RPC worked) and the resulting HoldStatus
 * is CAPTURED (what the hold's state now is). These are two different
 * questions and always will be, even after the hold is long settled.
 */
public enum HoldStatus {
    ACTIVE,     // funds reserved, not yet captured or released
    CAPTURED,   // funds have been debited; hold is now inert
    RELEASED,   // hold was voided before capture; funds returned to available balance
    EXPIRED     // hold's TTL elapsed before capture or release
}