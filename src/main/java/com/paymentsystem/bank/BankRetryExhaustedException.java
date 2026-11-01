package com.paymentsystem.bank;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.time.Duration;

/**
 * Exception thrown when all configured retry attempts have been exhausted for a BankClient operation.
 *
 * <p><strong>Architectural Role:</strong>
 * Extends {@link StatusRuntimeException} with status {@link Status#UNAVAILABLE} to preserve
 * gRPC error contract compatibility for existing callers while providing structured failure
 * diagnostics (operation name, attempt count, elapsed duration) without exposing sensitive
 * financial or customer payload data.</p>
 */
public class BankRetryExhaustedException extends StatusRuntimeException {

    private final String operationName;
    private final int attemptsMade;
    private final Duration elapsedDuration;

    /**
     * Constructs a BankRetryExhaustedException.
     *
     * @param operationName the banking operation name (e.g. "hold", "capture")
     * @param attemptsMade the total number of attempts executed before exhaustion
     * @param elapsedDuration the total duration elapsed across all attempts
     * @param cause the underlying {@link StatusRuntimeException} from the final attempt
     */
    public BankRetryExhaustedException(String operationName, int attemptsMade, Duration elapsedDuration, StatusRuntimeException cause) {
        super(Status.UNAVAILABLE
                .withDescription(String.format("Retry exhausted after %d attempts for operation '%s'",
                        attemptsMade, sanitizeOperationName(operationName)))
                .withCause(cause));
        this.operationName = sanitizeOperationName(operationName);
        this.attemptsMade = attemptsMade;
        this.elapsedDuration = elapsedDuration;
    }

    public String getOperationName() {
        return operationName;
    }

    public int getAttemptsMade() {
        return attemptsMade;
    }

    public Duration getElapsedDuration() {
        return elapsedDuration;
    }

    private static String sanitizeOperationName(String name) {
        return name != null ? name.replaceAll("[^a-zA-Z0-9_-]", "") : "unknown";
    }
}

