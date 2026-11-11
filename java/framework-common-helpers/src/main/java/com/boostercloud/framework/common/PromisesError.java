package com.boostercloud.framework.common;

import com.boostercloud.framework.types.errors.BoosterErrors;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown by {@link Promises#allSettledAndFulfilled} when at least one task failed. Port of {@code PromisesError}:
 * {@link #failedReasons()} holds every failure in input order, and the message is the failures joined with
 * {@code ". "} (each rendered like JavaScript prints an error, {@code "<Name>: <message>"}). The first reason is also
 * the cause and the others are suppressed exceptions, for stack traces.
 */
public class PromisesError extends RuntimeException {

  private final transient List<Throwable> failedReasons;

  /**
   * @param failedReasons the failures, in input order (at least one)
   */
  public PromisesError(List<Throwable> failedReasons) {
    super(failedReasons.stream().map(BoosterErrors::errorToString).collect(Collectors.joining(". ")),
        failedReasons.isEmpty() ? null : failedReasons.get(0));
    this.failedReasons = List.copyOf(failedReasons);
    for (int i = 1; i < this.failedReasons.size(); i++) {
      addSuppressed(this.failedReasons.get(i));
    }
  }

  /**
   * @return the failures, in input order
   */
  public List<Throwable> failedReasons() {
    return failedReasons;
  }
}
