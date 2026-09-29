package com.paymentsystem.model;

/**
 * The outcome of a single bank RPC call — did this specific call succeed
 * or fail. This says nothing about the standing state of a hold, account,
 * or transaction; see {@link HoldStatus} for that.
 */
public enum TransactionStatus {
    PENDING,
    HELD,
    CAPTURED,
    COMPLETED,
    REVERSED,
    FAILED
}