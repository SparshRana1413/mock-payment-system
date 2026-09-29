package com.paymentsystem.model;

/**
 * Why a bank RPC call failed. Only meaningful when the accompanying
 * {@link TransactionStatus} is FAILED — a SUCCEEDED response should never
 * carry a failure reason.
 */
public enum BankFailureReason {
    INSUFFICIENT_FUNDS,
    ACCOUNT_LOCKED,
    HOLD_NOT_FOUND,
    HOLD_ALREADY_CAPTURED,
    HOLD_ALREADY_RELEASED,
    HOLD_EXPIRED,
    CHAOS_INJECTED // the mock bank's deliberate random failure, not a "real" business failure
}