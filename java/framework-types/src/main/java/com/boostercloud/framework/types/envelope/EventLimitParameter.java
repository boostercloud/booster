package com.boostercloud.framework.types.envelope;

/**
 * Port of {@code EventLimitParameter}.
 */
public interface EventLimitParameter {
  /**
   * @return maximum number of results, or {@code null}
   */
  Integer limit();
}
