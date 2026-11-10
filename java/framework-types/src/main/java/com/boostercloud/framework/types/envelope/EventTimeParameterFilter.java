package com.boostercloud.framework.types.envelope;

/**
 * Port of {@code EventTimeParameterFilter}.
 */
public interface EventTimeParameterFilter extends EventLimitParameter {
  /**
   * @return ISO lower bound of {@code createdAt}, or {@code null}
   */
  String from();

  /**
   * @return ISO upper bound of {@code createdAt}, or {@code null}
   */
  String to();
}
