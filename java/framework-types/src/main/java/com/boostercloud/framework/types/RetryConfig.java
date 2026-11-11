package com.boostercloud.framework.types;

import java.util.List;

/**
 * Settings of {@code Retrier.retryWithBackoff}. Port of {@code RetryConfig}. Mutable, like the TypeScript object
 * ({@code config.eventStoreRetry.maxRetries = 5} works).
 *
 * <ul>
 *   <li>{@link #maxRetries} is the <b>total number of attempts</b> (3 means at most 3 calls and 2 sleeps);
 *   <li>the delay after the n-th failure is {@code min(initialDelay * backoffFactor^(n-1), maxDelay)} plus a jitter
 *       of {@code ±jitterFactor} of it, clipped to {@code [0, maxDelay]};
 *   <li>an error whose simple class name is in {@link #nonRetryableErrors} is never retried; when
 *       {@link #retryAllErrors} is {@code false} only errors named in {@link #retryableErrors} are retried;
 *       otherwise ({@code true} or {@code null}) every error is retried.
 * </ul>
 */
public class RetryConfig {
  /** Total number of attempts. */
  public int maxRetries;

  /** Delay before the first retry, in milliseconds. */
  public double initialDelay;

  /** Maximum delay between attempts, in milliseconds. */
  public double maxDelay;

  /** Multiplier of the exponential backoff. */
  public double backoffFactor;

  /** Random jitter factor, between 0 and 1. */
  public double jitterFactor;

  /** {@code false}: retry only {@link #retryableErrors}; {@code true} or {@code null}: retry every error. */
  public Boolean retryAllErrors;

  /** Simple class names of errors that are never retried, or {@code null}. */
  public List<String> nonRetryableErrors;

  /** Simple class names of errors retried when {@link #retryAllErrors} is {@code false}, or {@code null}. */
  public List<String> retryableErrors;

  /** Creates a configuration with every number at zero. */
  public RetryConfig() {}

  /**
   * @param maxRetries total number of attempts
   * @param initialDelay delay before the first retry, ms
   * @param maxDelay maximum delay, ms
   * @param backoffFactor backoff multiplier
   * @param jitterFactor jitter factor (0-1)
   */
  public RetryConfig(int maxRetries, double initialDelay, double maxDelay, double backoffFactor, double jitterFactor) {
    this.maxRetries = maxRetries;
    this.initialDelay = initialDelay;
    this.maxDelay = maxDelay;
    this.backoffFactor = backoffFactor;
    this.jitterFactor = jitterFactor;
  }

  /**
   * @return the framework default of {@code config.eventStoreRetry}: 3 attempts, 1000 ms initial delay, 30000 ms
   *     maximum delay, backoff factor 2, jitter 0.1
   */
  public static RetryConfig eventStoreDefaults() {
    return new RetryConfig(3, 1000, 30000, 2, 0.1);
  }

  /**
   * @return an independent copy (TypeScript {@code { ...config }})
   */
  public RetryConfig copy() {
    RetryConfig copy = new RetryConfig(maxRetries, initialDelay, maxDelay, backoffFactor, jitterFactor);
    copy.retryAllErrors = retryAllErrors;
    copy.nonRetryableErrors = nonRetryableErrors == null ? null : List.copyOf(nonRetryableErrors);
    copy.retryableErrors = retryableErrors == null ? null : List.copyOf(retryableErrors);
    return copy;
  }

  @Override
  public String toString() {
    return "RetryConfig{maxRetries=" + maxRetries + ", initialDelay=" + initialDelay + ", maxDelay=" + maxDelay
        + ", backoffFactor=" + backoffFactor + ", jitterFactor=" + jitterFactor + ", retryAllErrors=" + retryAllErrors
        + ", nonRetryableErrors=" + nonRetryableErrors + ", retryableErrors=" + retryableErrors + "}";
  }
}
