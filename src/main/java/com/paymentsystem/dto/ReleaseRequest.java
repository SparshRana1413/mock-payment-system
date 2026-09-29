package com.paymentsystem.dto;

import java.util.UUID;

public class ReleaseRequest {

    private final UUID idempotencyKey;
    private final UUID holdId;
    private final String reason; // optional, human-readable, e.g. "downstream credit failed"

    public ReleaseRequest(UUID idempotencyKey, UUID holdId, String reason) {
        this.idempotencyKey = idempotencyKey;
        this.holdId = holdId;
        this.reason = reason;
    }

    public UUID getIdempotencyKey() { return idempotencyKey; }
    public UUID getHoldId() { return holdId; }
    public String getReason() { return reason; }
}