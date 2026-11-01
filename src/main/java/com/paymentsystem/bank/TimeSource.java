package com.paymentsystem.bank;

/**
 * Strategy interface abstracting nanosecond wall-clock measurements.
 *
 * <p><strong>Architectural Role:</strong>
 * Decouples retry timeout and deadline calculations from {@link System#nanoTime()},
 * enabling deterministic, instantaneous timeout testing in isolated unit tests.</p>
 */
@FunctionalInterface
public interface TimeSource {

    /**
     * Returns the current reading of the ticker source in nanoseconds.
     *
     * @return current time in nanoseconds
     */
    long nanoTime();

    /**
     * Default production implementation delegating to {@link System#nanoTime()}.
     */
    TimeSource SYSTEM = System::nanoTime;
}

