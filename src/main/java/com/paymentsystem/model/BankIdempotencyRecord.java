package com.paymentsystem.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Operational step within a multi-phase banking workflow.
 */
enum Step {
    /** Fund authorization / hold reservation. */
    HOLD,
    /** Settlement and debiting of previously held funds. */
    CAPTURE,
    /** Cancellation and voiding of an active hold. */
    RELEASE,
    /** Compensating refund / reversal of a settled transaction. */
    REVERSE
}

/**
 * Execution status for a banking step idempotency record.
 */
enum Status {
    /** Operation is currently processing. */
    PENDING,
    /** Operation completed successfully. */
    COMPLETED,
    /** Operation resulted in a failure. */
    FAILED
}

/**
 * Record model representing bank-side deduplication tracking.
 *
 * <p><strong>Architectural Role:</strong>
 * Integration / Architectural Reference model. Reflects how the Mock Bank deduplicates
 * incoming RPC requests to guarantee idempotency (ADR-004), detecting request payload tampering
 * via {@code requestHash} and replaying {@code responseSnapshot} on duplicate calls.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a Mongoose schema or TypeScript interface representing an idempotency table
 * with a compound unique index on {@code { idempotencyKey: 1, step: 1 }}.</p>
 */
class BankIdempotencyRecord {
    UUID idempotencyKey;
    Step step;
    String requestHash;       // Hash of request payload to detect key reuse with altered parameters
    String responseSnapshot;  // Cached serialized response to replay on duplicate requests
    Status status;            // Processing state: PENDING, COMPLETED, or FAILED
    Instant recordedAt;
}
// Unique database constraint expected on composite key: (idempotencyKey, step)