package com.paymentsystem.bank;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Centralized configuration for Mock Bank gRPC retry behaviour and lifecycle deadlines.
 *
 * <p><strong>Architectural Role:</strong>
 * Single source of truth for:
 * <ul>
 *   <li>Maximum retry attempts (total attempts including initial)</li>
 *   <li>Initial backoff duration</li>
 *   <li>Exponential backoff multiplier</li>
 *   <li>Maximum backoff duration cap</li>
 *   <li>Cumulative retry lifecycle deadline / timeout</li>
 * </ul>
 * </p>
 */
@Component
public class BankRetryConfig {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    public static final long DEFAULT_INITIAL_BACKOFF_MS = 100L;
    public static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    public static final long DEFAULT_MAX_BACKOFF_MS = 2000L;
    public static final long DEFAULT_RETRY_TIMEOUT_MS = 5000L;

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final double backoffMultiplier;
    private final Duration maxBackoff;
    private final Duration retryTimeout;

    /**
     * Default constructor initializing standard default retry settings (3 attempts, 100ms initial, 2.0x, 2000ms max backoff, 5000ms timeout).
     */
    public BankRetryConfig() {
        this(DEFAULT_MAX_ATTEMPTS,
             DEFAULT_INITIAL_BACKOFF_MS,
             DEFAULT_BACKOFF_MULTIPLIER,
             DEFAULT_MAX_BACKOFF_MS,
             DEFAULT_RETRY_TIMEOUT_MS);
    }

    /**
     * Programmatic constructor with default 5000ms retry timeout.
     *
     * @param maxAttempts maximum total invocation attempts
     * @param initialBackoffMs initial backoff delay in milliseconds
     * @param backoffMultiplier exponential multiplier
     * @param maxBackoffMs maximum backoff delay ceiling in milliseconds
     */
    public BankRetryConfig(int maxAttempts,
                           long initialBackoffMs,
                           double backoffMultiplier,
                           long maxBackoffMs) {
        this(maxAttempts, initialBackoffMs, backoffMultiplier, maxBackoffMs, DEFAULT_RETRY_TIMEOUT_MS);
    }

    /**
     * Spring-managed constructor injecting configurable application properties.
     *
     * @param maxAttempts maximum total invocation attempts (must be &gt;= 1)
     * @param initialBackoffMs initial backoff delay in milliseconds
     * @param backoffMultiplier exponential multiplier (must be &gt;= 1.0)
     * @param maxBackoffMs maximum backoff delay ceiling in milliseconds
     * @param retryTimeoutMs cumulative retry deadline in milliseconds (must be &gt; 0)
     */
    @Autowired
    public BankRetryConfig(
            @Value("${bank.grpc.retry.max-attempts:3}") int maxAttempts,
            @Value("${bank.grpc.retry.initial-backoff-ms:100}") long initialBackoffMs,
            @Value("${bank.grpc.retry.backoff-multiplier:2.0}") double backoffMultiplier,
            @Value("${bank.grpc.retry.max-backoff-ms:2000}") long maxBackoffMs,
            @Value("${bank.grpc.retry.timeout-ms:5000}") long retryTimeoutMs) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, got: " + maxAttempts);
        }
        if (initialBackoffMs < 0) {
            throw new IllegalArgumentException("initialBackoffMs must not be negative, got: " + initialBackoffMs);
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be at least 1.0, got: " + backoffMultiplier);
        }
        if (maxBackoffMs < initialBackoffMs) {
            throw new IllegalArgumentException("maxBackoffMs (" + maxBackoffMs + ") must not be less than initialBackoffMs (" + initialBackoffMs + ")");
        }
        if (retryTimeoutMs <= 0) {
            throw new IllegalArgumentException("retryTimeoutMs must be positive, got: " + retryTimeoutMs);
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoff = Duration.ofMillis(initialBackoffMs);
        this.backoffMultiplier = backoffMultiplier;
        this.maxBackoff = Duration.ofMillis(maxBackoffMs);
        this.retryTimeout = Duration.ofMillis(retryTimeoutMs);
    }

    /**
     * Custom programmatic constructor using {@link Duration} instances.
     *
     * @param maxAttempts maximum total invocation attempts
     * @param initialBackoff initial backoff duration
     * @param backoffMultiplier exponential multiplier
     * @param maxBackoff maximum backoff duration cap
     * @param retryTimeout cumulative retry deadline duration
     */
    public BankRetryConfig(int maxAttempts,
                           Duration initialBackoff,
                           double backoffMultiplier,
                           Duration maxBackoff,
                           Duration retryTimeout) {
        this(maxAttempts,
             initialBackoff != null ? initialBackoff.toMillis() : DEFAULT_INITIAL_BACKOFF_MS,
             backoffMultiplier,
             maxBackoff != null ? maxBackoff.toMillis() : DEFAULT_MAX_BACKOFF_MS,
             retryTimeout != null ? retryTimeout.toMillis() : DEFAULT_RETRY_TIMEOUT_MS);
    }

    /**
     * Programmatic constructor using Duration instances with default timeout.
     *
     * @param maxAttempts maximum total invocation attempts
     * @param initialBackoff initial backoff duration
     * @param backoffMultiplier exponential multiplier
     * @param maxBackoff maximum backoff duration cap
     */
    public BankRetryConfig(int maxAttempts, Duration initialBackoff, double backoffMultiplier, Duration maxBackoff) {
        this(maxAttempts, initialBackoff, backoffMultiplier, maxBackoff, Duration.ofMillis(DEFAULT_RETRY_TIMEOUT_MS));
    }

    /**
     * Calculates the exponential backoff duration for a given 1-based completed attempt index.
     *
     * <p>Formula:
     * {@code delay = min(initialBackoff * (multiplier ^ (attempt - 1)), maxBackoff)}
     *
     * <p>Includes safe arithmetic guarding against floating-point and integer overflow for large attempts.</p>
     *
     * @param attempt the 1-based completed attempt index
     * @return the calculated backoff Duration, capped at {@link #getMaxBackoff()}
     */
    public Duration calculateBackoff(int attempt) {
        if (attempt <= 1) {
            return initialBackoff;
        }
        // Guard against overflow for large powers
        if (attempt > 30) {
            return maxBackoff;
        }
        double factor = Math.pow(backoffMultiplier, attempt - 1);
        if (Double.isInfinite(factor) || Double.isNaN(factor) || factor > Long.MAX_VALUE) {
            return maxBackoff;
        }
        double calculatedMs = initialBackoff.toMillis() * factor;
        if (calculatedMs >= maxBackoff.toMillis()) {
            return maxBackoff;
        }
        return Duration.ofMillis((long) calculatedMs);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public Duration getRetryTimeout() {
        return retryTimeout;
    }
}
