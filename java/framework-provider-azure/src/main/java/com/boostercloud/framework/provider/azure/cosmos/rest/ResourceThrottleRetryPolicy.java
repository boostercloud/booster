package com.boostercloud.framework.provider.azure.cosmos.rest;

import com.boostercloud.framework.provider.azure.cosmos.CosmosException;

/**
 * Port of the JavaScript SDK's {@code ResourceThrottleRetryPolicy}: retries a request answered with 429 (request rate
 * too large) up to {@code maxRetryAttemptCount} times, waiting the fixed interval when configured or the server's
 * {@code x-ms-retry-after-ms} otherwise, while the cumulative wait stays below {@code maxWaitTimeInSeconds}. One
 * instance per logical operation.
 */
public final class ResourceThrottleRetryPolicy {

  /** HTTP status of a throttled request. */
  public static final int TOO_MANY_REQUESTS = 429;

  private final double maxTries;
  private final double fixedRetryIntervalInMs;
  private final double timeoutInMs;
  private int currentRetryAttemptCount;
  private double cumulativeWaitTimeInMs;
  private double retryAfterInMs;

  /**
   * @param options the retry options
   */
  public ResourceThrottleRetryPolicy(CosmosRetryOptions options) {
    this.maxTries = options.maxRetryAttemptCount();
    this.fixedRetryIntervalInMs = options.fixedRetryIntervalInMilliseconds();
    this.timeoutInMs = options.maxWaitTimeInSeconds() * 1000;
  }

  /**
   * @param error a failed request
   * @return whether to retry it, after waiting {@link #retryAfterInMs()}
   */
  public boolean shouldRetry(CosmosException error) {
    if (error == null || error.code() != TOO_MANY_REQUESTS) {
      return false;
    }
    if (currentRetryAttemptCount < maxTries) {
      currentRetryAttemptCount++;
      retryAfterInMs = 0;
      if (fixedRetryIntervalInMs != 0 && !Double.isNaN(fixedRetryIntervalInMs)) {
        retryAfterInMs = fixedRetryIntervalInMs;
      } else if (error.retryAfterInMs() != null) {
        retryAfterInMs = error.retryAfterInMs();
      }
      if (cumulativeWaitTimeInMs < timeoutInMs) {
        cumulativeWaitTimeInMs += retryAfterInMs;
        return true;
      }
    }
    return false;
  }

  /**
   * @return how long to wait before the next attempt, in milliseconds
   */
  public long retryAfterInMs() {
    return (long) retryAfterInMs;
  }

  /**
   * @return how many retries were granted so far
   */
  public int currentRetryAttemptCount() {
    return currentRetryAttemptCount;
  }
}
