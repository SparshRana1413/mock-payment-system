package com.paymentsystem.bank;

import java.time.Duration;

/**
 * Functional interface abstracting thread sleep during exponential backoff.
 *
 * <p><strong>Architectural Role:</strong>
 * Decouples backoff delay execution from {@link Thread#sleep(long)}, enabling
 * deterministic, non-blocking unit tests without introducing artificial latency.</p>
 */
@FunctionalInterface
public interface Sleeper {

    /**
     * Pauses execution for the specified duration.
     *
     * @param duration the duration to pause
     * @throws InterruptedException if interrupted while waiting
     */
    void sleep(Duration duration) throws InterruptedException;

    /**
     * Default production implementation delegating to {@link Thread#sleep(long)}.
     */
    Sleeper DEFAULT = duration -> {
        if (duration != null && !duration.isZero() && !duration.isNegative()) {
            Thread.sleep(duration.toMillis());
        }
    };

    /**
     * No-op implementation for high-speed, deterministic unit testing.
     */
    Sleeper NO_OP = duration -> {};
}

