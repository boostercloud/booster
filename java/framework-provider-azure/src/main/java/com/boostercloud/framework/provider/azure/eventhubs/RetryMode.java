package com.boostercloud.framework.provider.azure.eventhubs;

/** {@code RetryMode} of {@code @azure/core-amqp}. */
public enum RetryMode {
  /** Exponentially growing delays with jitter. */
  EXPONENTIAL,
  /** The same delay between attempts. */
  FIXED
}
