package com.boostercloud.framework.provider.local.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * The time source of the {@link Scheduler}: the current instant, the zone cron expressions are evaluated in, and a
 * way to wait for an instant. {@link #system()} is the wall clock in the JVM's default zone (TypeScript's
 * {@code node-schedule} uses the process's local time zone); tests use a manual implementation.
 */
public interface SchedulerClock {

  /**
   * @return the zone the cron expressions are evaluated in
   */
  ZoneId zone();

  /**
   * @return the current instant
   */
  Instant now();

  /**
   * Blocks until {@link #now()} reaches {@code deadline} (it may return earlier; callers check again).
   *
   * @param deadline the instant to wait for
   * @throws InterruptedException when the waiting thread is interrupted (the scheduler is stopping)
   */
  void sleepUntil(Instant deadline) throws InterruptedException;

  /**
   * @return the wall clock in the JVM's default zone
   */
  static SchedulerClock system() {
    return system(ZoneId.systemDefault());
  }

  /**
   * @param zone the zone
   * @return the wall clock evaluated in {@code zone}
   */
  static SchedulerClock system(ZoneId zone) {
    Objects.requireNonNull(zone, "zone");
    return new SchedulerClock() {
      private static final Duration MAX_SLEEP = Duration.ofSeconds(30);

      @Override
      public ZoneId zone() {
        return zone;
      }

      @Override
      public Instant now() {
        return Instant.now();
      }

      @Override
      public void sleepUntil(Instant deadline) throws InterruptedException {
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isNegative() || remaining.isZero()) {
          return;
        }
        // wake up regularly so that wall-clock adjustments are noticed
        Thread.sleep(remaining.compareTo(MAX_SLEEP) > 0 ? MAX_SLEEP : remaining);
      }
    };
  }
}
