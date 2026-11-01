package com.paymentsystem.bank;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Centralized, reusable retry executor for BankService gRPC operations.
 *
 * <p><strong>Architectural Role:</strong>
 * Implements centralized transient error detection, exponential backoff, lifecycle deadlines,
 * and retry termination management. Guarantees that financial requests are reused verbatim across
 * attempts without altering transaction IDs or idempotency keys, preventing duplicate financial mutations.</p>
 */
@Component
public class BankRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(BankRetryExecutor.class);

    private final BankRetryConfig config;
    private final Sleeper sleeper;
    private final TimeSource timeSource;

    /**
     * Default constructor with standard configuration, Thread.sleep, and System.nanoTime.
     */
    public BankRetryExecutor() {
        this(new BankRetryConfig(), Sleeper.DEFAULT, TimeSource.SYSTEM);
    }

    /**
     * Constructor using specified configuration with standard Thread.sleep and System.nanoTime.
     *
     * @param config the retry configuration
     */
    public BankRetryExecutor(BankRetryConfig config) {
        this(config, Sleeper.DEFAULT, TimeSource.SYSTEM);
    }

    /**
     * Constructor supporting custom sleeper (e.g. for deterministic unit testing).
     *
     * @param config the retry configuration
     * @param sleeper the delay mechanism (defaults to {@link Sleeper#DEFAULT} if null)
     */
    public BankRetryExecutor(BankRetryConfig config, Sleeper sleeper) {
        this(config, sleeper, TimeSource.SYSTEM);
    }

    /**
     * Full constructor supporting an injectable {@link Sleeper} and {@link TimeSource} for non-blocking unit tests.
     *
     * @param config the retry configuration
     * @param sleeper the delay mechanism (defaults to {@link Sleeper#DEFAULT} if null)
     * @param timeSource the time source (defaults to {@link TimeSource#SYSTEM} if null)
     */
    @Autowired
    public BankRetryExecutor(
            BankRetryConfig config,
            @Autowired(required = false) Sleeper sleeper,
            @Autowired(required = false) TimeSource timeSource) {
        this.config = config != null ? config : new BankRetryConfig();
        this.sleeper = sleeper != null ? sleeper : Sleeper.DEFAULT;
        this.timeSource = timeSource != null ? timeSource : TimeSource.SYSTEM;
    }

    public BankRetryConfig getConfig() {
        return config;
    }

    public Sleeper getSleeper() {
        return sleeper;
    }

    public TimeSource getTimeSource() {
        return timeSource;
    }

    /**
     * Determines whether a gRPC exception is transient and eligible for retry.
     * Specifically, returns true for {@link Status.Code#UNAVAILABLE}.
     * Permanent business errors, invalid arguments, and authorization rejections are not retried.
     *
     * @param ex the gRPC exception
     * @return true if the failure is transient (UNAVAILABLE), false otherwise
     */
    public boolean isRetryable(StatusRuntimeException ex) {
        if (ex == null || ex.getStatus() == null) {
            return false;
        }
        return ex.getStatus().getCode() == Status.Code.UNAVAILABLE;
    }

    /**
     * Executes the given operation, applying exponential backoff and lifecycle deadline enforcement
     * when transient {@link Status.Code#UNAVAILABLE} errors are encountered.
     *
     * @param <T> the return type of the operation
     * @param operation the supplier executing the gRPC stub call
     * @param operationName the human-readable name of the RPC operation (for logging and diagnostics)
     * @return the result returned by the successful invocation
     * @throws StatusRuntimeException if a non-retryable error occurs
     * @throws BankRetryExhaustedException if all configured retry attempts fail
     * @throws BankRetryTimeoutException if the cumulative retry deadline expires
     */
    public <T> T execute(Supplier<T> operation, String operationName) {
        if (operation == null) {
            throw new IllegalArgumentException("Operation must not be null");
        }

        int maxAttempts = config.getMaxAttempts();
        Duration retryTimeout = config.getRetryTimeout();
        long startNano = timeSource.nanoTime();
        long deadlineNano = (retryTimeout != null && !retryTimeout.isZero() && !retryTimeout.isNegative())
                ? startNano + retryTimeout.toNanos()
                : Long.MAX_VALUE;

        StatusRuntimeException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // Check if retry deadline has already expired before starting the attempt
            long currentNano = timeSource.nanoTime();
            if (currentNano >= deadlineNano) {
                log.warn("Retry deadline of {}ms expired before attempt {} for operation '{}'",
                        retryTimeout != null ? retryTimeout.toMillis() : 0, attempt, operationName);
                throw new BankRetryTimeoutException(operationName, attempt - 1, retryTimeout, lastException);
            }

            try {
                return operation.get();
            } catch (StatusRuntimeException e) {
                if (!isRetryable(e)) {
                    log.warn("Non-retryable gRPC error during {} on attempt {}/{} (status={}): {}",
                            operationName, attempt, maxAttempts, e.getStatus().getCode(), e.getMessage());
                    throw e;
                }

                lastException = e;

                // Stop if maximum attempt count is reached
                if (attempt >= maxAttempts) {
                    Duration elapsed = Duration.ofNanos(timeSource.nanoTime() - startNano);
                    log.warn("Exhausted all {} retry attempts for {} due to transient error: {}",
                            maxAttempts, operationName, e.getMessage());
                    throw new BankRetryExhaustedException(operationName, maxAttempts, elapsed, lastException);
                }

                // Check remaining time against cumulative retry deadline
                long nowNano = timeSource.nanoTime();
                long remainingNanos = deadlineNano - nowNano;
                if (remainingNanos <= 0) {
                    log.warn("Retry deadline of {}ms expired after attempt {} for operation '{}'",
                            retryTimeout != null ? retryTimeout.toMillis() : 0, attempt, operationName);
                    throw new BankRetryTimeoutException(operationName, attempt, retryTimeout, lastException);
                }

                // Calculate exponential backoff and cap it at the remaining deadline time
                Duration delay = config.calculateBackoff(attempt);
                Duration actualDelay = delay;
                if (delay.toNanos() > remainingNanos) {
                    actualDelay = Duration.ofNanos(remainingNanos);
                }

                log.info("Transient UNAVAILABLE error during {} on attempt {}/{}. Retrying in {}ms...",
                        operationName, attempt, maxAttempts, actualDelay.toMillis());

                try {
                    sleeper.sleep(actualDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new StatusRuntimeException(Status.CANCELLED
                            .withDescription("Retry interrupted for operation " + operationName)
                            .withCause(ie));
                }
            }
        }

        Duration elapsed = Duration.ofNanos(timeSource.nanoTime() - startNano);
        throw new BankRetryExhaustedException(operationName, maxAttempts, elapsed, lastException);
    }
}
