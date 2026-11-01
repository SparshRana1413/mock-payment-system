package com.paymentsystem.bank;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.time.Duration;

/**
 * Exception thrown when the cumulative retry deadline expires before a BankClient operation succeeds.
 *
 * <p><strong>Architectural Role:</strong>
 * Extends {@link StatusRuntimeException} with status {@link Status#DEADLINE_EXCEEDED} to preserve
 * gRPC error contract compatibility while providing structured failure diagnostics
 * without exposing sensitive financial or customer payload data.</p>
 */
public class BankRetryTimeoutException extends StatusRuntimeException {

    private final String operationName;
    private final int attemptsMade;
    private final Duration timeoutLimit;

    /**
     * Constructs a BankRetryTimeoutException.
     *
     * @param operationName the banking operation name (e.g. "hold", "capture")
     * @param attemptsMade the number of attempts completed prior to timeout
     * @param timeoutLimit the configured retry timeout ceiling
     * @param cause the underlying {@link StatusRuntimeException} from the most recent attempt
     */
    public BankRetryTimeoutException(String operationName, int attemptsMade, Duration timeoutLimit, StatusRuntimeException cause) {
        super(Status.DEADLINE_EXCEEDED
                .withDescription(String.format("Retry timeout of %dms exceeded after %d attempts for operation '%s'",
                        timeoutLimit != null ? timeoutLimit.toMillis() : 0, attemptsMade, sanitizeOperationName(operationName)))
                .withCause(cause));
        this.operationName = sanitizeOperationName(operationName);
        this.attemptsMade = attemptsMade;
        this.timeoutLimit = timeoutLimit;
    }

    public String getOperationName() {
        return operationName;
    }

    public int getAttemptsMade() {
        return attemptsMade;
    }

    public Duration getTimeoutLimit() {
        return timeoutLimit;
    }

    private static String sanitizeOperationName(String name) {
        return name != null ? name.replaceAll("[^a-zA-Z0-9_-]", "") : "unknown";
    }
}

