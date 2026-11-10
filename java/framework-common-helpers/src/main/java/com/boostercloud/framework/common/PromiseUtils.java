package com.boostercloud.framework.common;

/**
 * Port of {@code promise_utils.ts}.
 */
public final class PromiseUtils {
  private PromiseUtils() {}

  /**
   * @param result a settled result
   * @return whether it is rejected
   */
  public static boolean isRejected(SettledResult<?> result) {
    return result instanceof SettledResult.Rejected<?>;
  }

  /**
   * @param result a settled result
   * @return whether it is fulfilled
   */
  public static boolean isFulfilled(SettledResult<?> result) {
    return result instanceof SettledResult.Fulfilled<?>;
  }
}
