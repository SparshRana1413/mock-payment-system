package com.paymentsystem.dto;

import java.util.UUID;

/**
 * Request DTO for the bank's Capture RPC. No amount field — Capture always
 * captures the full held amount (partial capture was ruled out: the bank
 * validates funds at Hold time, so there's nothing left to partially do).
 */
public class CaptureRequest {

    private final UUID idempotencyKey;
    private final UUID holdId;

    public CaptureRequest(UUID idempotencyKey, UUID holdId) {
        this.idempotencyKey = idempotencyKey;
        this.holdId = holdId;
    }

    public UUID getIdempotencyKey() { return idempotencyKey; }
    public UUID getHoldId() { return holdId; }
}