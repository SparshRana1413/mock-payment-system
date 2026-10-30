package com.paymentsystem.dto;

import java.util.UUID;

/**
 * Request DTO for the Mock Bank's Capture RPC operation.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / DTO layer. Carries the parameters required to settle and debit previously
 * held funds via {@code payment.bank.v1.BankService/Capture}.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a TypeScript interface/DTO:
 * {@code interface CaptureRequest { idempotencyKey: string; holdId: string; }}.</p>
 *
 * <p><strong>Design Note (No amount field):</strong>
 * Capture always debits the full held amount (partial capture is not supported;
 * funds were already verified and reserved during the Hold step).</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Converting to the Protobuf {@code payment.bank.v1.CaptureRequest} and issuing the gRPC call
 * belongs in {@link com.paymentsystem.bank.BankClient}.</p>
 */
public class CaptureRequest {

    private final UUID idempotencyKey;
    private final UUID holdId;

    /**
     * Constructs a CaptureRequest with caller-provided idempotency key and target holdId.
     *
     * @param idempotencyKey unique key guaranteeing exactly-once capture execution
     * @param holdId identifier of the active authorization hold to settle
     */
    public CaptureRequest(UUID idempotencyKey, UUID holdId) {
        this.idempotencyKey = idempotencyKey;
        this.holdId = holdId;
    }

    public UUID getIdempotencyKey() { return idempotencyKey; }
    public UUID getHoldId() { return holdId; }
}