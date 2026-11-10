package com.boostercloud.application.tester.graphql;

import java.util.function.DoubleSupplier;

/**
 * Exponential backoff with optional jitter: port of the {@code backo2} package used by
 * {@code subscriptions-transport-ws}. {@code duration()} returns {@code min * factor^attempts} (attempts then
 * incremented), shifted by up to {@code jitter * 100}% up or down, capped at {@code max} and truncated to an
 * integer like JavaScript's {@code | 0}.
 */
final class Backoff {

  private final double min;
  private final double max;
  private final double factor;
  private final double jitter;
  private final DoubleSupplier random;
  private int attempts;

  /**
   * @param min the first duration in milliseconds (backo2 default 100)
   * @param max the maximum duration (backo2 default 10000)
   * @param factor the growth factor (backo2 default 2)
   * @param jitter the jitter in {@code (0, 1]}, anything else disables it
   * @param random the random source ({@code Math.random})
   */
  Backoff(double min, double max, double factor, double jitter, DoubleSupplier random) {
    this.min = min;
    this.max = max;
    this.factor = factor;
    this.jitter = jitter > 0 && jitter <= 1 ? jitter : 0;
    this.random = random;
  }

  /**
   * @return the next duration in milliseconds
   */
  long duration() {
    double ms = min * Math.pow(factor, attempts++);
    if (jitter > 0) {
      double rand = random.getAsDouble();
      double deviation = Math.floor(rand * jitter * ms);
      ms = (((int) Math.floor(rand * 10)) & 1) == 0 ? ms - deviation : ms + deviation;
    }
    return toInt32(Math.min(ms, max));
  }

  /** Restarts from the first duration. */
  void reset() {
    attempts = 0;
  }

  /**
   * @return the number of durations handed out since the last reset
   */
  int attempts() {
    return attempts;
  }

  private static long toInt32(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return 0;
    }
    return (int) (long) value;
  }
}
