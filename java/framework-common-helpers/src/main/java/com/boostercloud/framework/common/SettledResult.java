package com.boostercloud.framework.common;

import java.util.Objects;

/**
 * The outcome of one task joined by {@link Promises}: the Java form of {@code PromiseSettledResult}
 * ({@code { status: 'fulfilled', value } | { status: 'rejected', reason }}).
 *
 * @param <T> the value type
 */
public sealed interface SettledResult<T> permits SettledResult.Fulfilled, SettledResult.Rejected {

  /**
   * @return {@code "fulfilled"} or {@code "rejected"}
   */
  String status();

  /**
   * A task that completed normally.
   *
   * @param value its result (may be {@code null})
   * @param <T> the value type
   */
  record Fulfilled<T>(T value) implements SettledResult<T> {
    @Override
    public String status() {
      return "fulfilled";
    }
  }

  /**
   * A task that failed.
   *
   * @param reason why it failed
   * @param <T> the value type
   */
  record Rejected<T>(Throwable reason) implements SettledResult<T> {
    /** Canonical constructor; rejects {@code null}. */
    public Rejected {
      Objects.requireNonNull(reason, "reason");
    }

    @Override
    public String status() {
      return "rejected";
    }
  }
}
