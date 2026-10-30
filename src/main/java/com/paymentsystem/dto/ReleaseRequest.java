package com.paymentsystem.dto;

import java.util.UUID;

/**
 * Request DTO for the Mock Bank's Release RPC operation.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / DTO layer. Requests cancellation of an active authorization hold,
 * releasing previously reserved funds back to the user's available balance (used frequently
 * as a compensating step in saga workflows when downstream steps fail).</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript interface:
 * {@code interface ReleaseRequest { idempotencyKey: string; holdId: string; reason?: string; }}.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Saga orchestration logic that decides when to trigger a hold release belongs in the service layer.</p>
 */
public class ReleaseRequest {

    private final UUID idempotencyKey;
    private final UUID holdId;
    private final String reason; // Optional human-readable justification (e.g., "Downstream credit failed")

    /**
     * Constructs a ReleaseRequest.
     *
     * @param idempotencyKey unique key guaranteeing exactly-once release execution
     * @param holdId the ID of the active hold to cancel
     * @param reason human-readable reason for voiding the hold
     */
    public ReleaseRequest(UUID idempotencyKey, UUID holdId, String reason) {
        this.idempotencyKey = idempotencyKey;
        this.holdId = holdId;
        this.reason = reason;
    }

    public UUID getIdempotencyKey() { return idempotencyKey; }
    public UUID getHoldId() { return holdId; }
    public String getReason() { return reason; }
}