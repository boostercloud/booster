package com.boostercloud.framework.common;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.RetryConfig;
import com.boostercloud.framework.types.errors.BoosterErrors;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Port of {@code retrier.ts}: retry on an error class without delay ({@link #retryIfError}) and exponential backoff
 * with jitter ({@link #retryWithBackoff}). In both, the maximum is the <b>total number of attempts</b>.
 */
public final class Retrier {

  /** Default number of attempts of {@link #retryIfError}. */
  public static final int DEFAULT_MAX_RETRIES = 1000;

  private Retrier() {}

  /** Logic retried by {@link #retryIfError}; receives the 1-based attempt number. */
  @FunctionalInterface
  public interface RetriableLogic<T> {
    /**
     * @param tryNumber the attempt number, starting at 1
     * @return the result
     */
    T run(int tryNumber);
  }

  /** Waits between attempts; the default sleeps the current (virtual) thread. */
  @FunctionalInterface
  public interface Sleeper {
    /** Sleeps with {@link Thread#sleep(long)}; an interruption cancels the retries. */
    Sleeper THREAD_SLEEP = millis -> {
      try {
        Thread.sleep(millis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new CancellationException("Interrupted while waiting to retry");
      }
    };

    /**
     * @param millis milliseconds to wait
     */
    void sleep(long millis);
  }

  /**
   * {@link #retryIfError(RetriableLogic, Class, Logger, int)} with no logger and 1000 attempts.
   *
   * @param logicToRetry the logic
   * @param errorClassThatRetries errors of this class (or a subclass) are retried
   * @param <T> the result type
   * @return the result of the first successful attempt
   */
  public static <T> T retryIfError(RetriableLogic<T> logicToRetry, Class<? extends Throwable> errorClassThatRetries) {
    return retryIfError(logicToRetry, errorClassThatRetries, null, DEFAULT_MAX_RETRIES);
  }

  /**
   * {@link #retryIfError(RetriableLogic, Class, Logger, int)} with 1000 attempts.
   *
   * @param logicToRetry the logic
   * @param errorClassThatRetries errors of this class (or a subclass) are retried
   * @param logger debug logger, or {@code null}
   * @param <T> the result type
   * @return the result of the first successful attempt
   */
  public static <T> T retryIfError(
      RetriableLogic<T> logicToRetry, Class<? extends Throwable> errorClassThatRetries, Logger logger) {
    return retryIfError(logicToRetry, errorClassThatRetries, logger, DEFAULT_MAX_RETRIES);
  }

  /**
   * {@code retryIfError(logic, errorClass, logger?, maxRetries = 1000)}: runs the logic until it succeeds, retrying
   * immediately (no delay) while it throws an instance of {@code errorClassThatRetries} (subclasses included). Any
   * other error is rethrown unchanged after the first attempt.
   *
   * @param logicToRetry the logic; receives the attempt number (1-based)
   * @param errorClassThatRetries errors of this class are retried
   * @param logger debug logger, or {@code null}
   * @param maxRetries total number of attempts
   * @param <T> the result type
   * @return the result of the first successful attempt
   * @throws IllegalStateException {@code [retryIfError] Reached the maximum number of retries (N), but still getting
   *     the following error: <error>} when every attempt failed (the last error is the cause)
   */
  public static <T> T retryIfError(
      RetriableLogic<T> logicToRetry, Class<? extends Throwable> errorClassThatRetries, Logger logger, int maxRetries) {
    Objects.requireNonNull(logicToRetry, "logicToRetry");
    Objects.requireNonNull(errorClassThatRetries, "errorClassThatRetries");
    Throwable errorAfterMaxTries = null;
    for (int tryNumber = 1; tryNumber <= maxRetries; tryNumber++) {
      try {
        if (logger != null) {
          logger.debug("[retryIfError] Try number " + tryNumber);
        }
        T result = logicToRetry.run(tryNumber);
        if (logger != null) {
          logger.debug("[retryIfError] Succeeded after " + tryNumber + " retries");
        }
        return result;
      } catch (Throwable error) {
        checkRetryError(error, errorClassThatRetries, logger);
        errorAfterMaxTries = error;
      }
    }
    throw new IllegalStateException("[retryIfError] Reached the maximum number of retries (" + maxRetries
        + "), but still getting the following error: " + BoosterErrors.errorToString(errorAfterMaxTries), errorAfterMaxTries);
  }

  private static void checkRetryError(Throwable error, Class<? extends Throwable> errorClassThatRetries, Logger logger) {
    if (!errorClassThatRetries.isInstance(error)) {
      if (logger != null) {
        logger.debug("[checkRetryError] Logic failed with an error that must not be retried. Rethrowing");
      }
      throw Exceptions.sneakyThrow(error);
    }
  }

  /**
   * {@link #retryWithBackoff(Supplier, RetryConfig, Logger)} without logger.
   *
   * @param logicToRetry the logic
   * @param config the retry settings
   * @param <T> the result type
   * @return the result of the first successful attempt
   */
  public static <T> T retryWithBackoff(Supplier<T> logicToRetry, RetryConfig config) {
    return retryWithBackoff(logicToRetry, config, null);
  }

  /**
   * {@code retryWithBackoff(logic, config, logger?)}.
   *
   * @param logicToRetry the logic
   * @param config the retry settings
   * @param logger logger, or {@code null}
   * @param <T> the result type
   * @return the result of the first successful attempt
   */
  public static <T> T retryWithBackoff(Supplier<T> logicToRetry, RetryConfig config, Logger logger) {
    return retryWithBackoff(logicToRetry, config, logger, Sleeper.THREAD_SLEEP, () -> ThreadLocalRandom.current().nextDouble());
  }

  /**
   * Runs the logic up to {@code config.maxRetries} times, sleeping {@link #calculateRetryDelay} between attempts.
   * On each failure: if the error is not retryable it is rethrown at once (debug log
   * {@code [retryWithBackoff] Error <Name> is not retryable, failing immediately}); if it was the last attempt it is
   * rethrown (error log {@code [retryWithBackoff] Failed after N attempts}, with the error); otherwise a debug log
   * {@code [retryWithBackoff] Attempt N failed, retrying in <delay>ms...} precedes the sleep. Success logs nothing.
   *
   * <p>Errors are matched by simple class name against {@code nonRetryableErrors} (never retried) and, when
   * {@code retryAllErrors} is {@code false}, {@code retryableErrors}. {@link Error}s (e.g. {@code OutOfMemoryError})
   * are never caught.
   *
   * @param logicToRetry the logic
   * @param config the retry settings
   * @param logger logger, or {@code null}
   * @param sleeper how to wait (inject a fake in tests)
   * @param random uniform random numbers in {@code [0, 1)} for the jitter
   * @param <T> the result type
   * @return the result of the first successful attempt
   * @throws IllegalArgumentException when {@code maxRetries <= 0} (TypeScript then throws {@code undefined} without
   *     running the logic)
   */
  public static <T> T retryWithBackoff(
      Supplier<T> logicToRetry, RetryConfig config, Logger logger, Sleeper sleeper, DoubleSupplier random) {
    Objects.requireNonNull(logicToRetry, "logicToRetry");
    Objects.requireNonNull(config, "config");
    if (config.maxRetries <= 0) {
      throw new IllegalArgumentException(
          "[retryWithBackoff] maxRetries must be at least 1, but it is " + config.maxRetries);
    }
    int attempts = 0;
    while (true) {
      try {
        return logicToRetry.get();
      } catch (Exception error) {
        attempts++;
        if (!shouldRetryError(error, config)) {
          if (logger != null) {
            logger.debug("[retryWithBackoff] Error " + BoosterErrors.errorName(error) + " is not retryable, failing immediately");
          }
          throw Exceptions.sneakyThrow(error);
        }
        if (attempts >= config.maxRetries) {
          if (logger != null) {
            logger.error("[retryWithBackoff] Failed after " + attempts + " attempts", error);
          }
          throw Exceptions.sneakyThrow(error);
        }
        double delay = calculateRetryDelay(attempts, config, random);
        if (logger != null) {
          logger.debug("[retryWithBackoff] Attempt " + attempts + " failed, retrying in " + BoosterJson.formatNumber(delay) + "ms...");
        }
        sleeper.sleep((long) delay);
      }
    }
  }

  /**
   * {@code calculateRetryDelay(attempt, config)} with a random jitter.
   *
   * @param attempt the number of failures so far (1-based)
   * @param config the retry settings
   * @return the delay in milliseconds (not rounded)
   */
  public static double calculateRetryDelay(int attempt, RetryConfig config) {
    return calculateRetryDelay(attempt, config, () -> ThreadLocalRandom.current().nextDouble());
  }

  /**
   * {@code base = min(initialDelay * backoffFactor^(attempt-1), maxDelay)};
   * {@code jitter = base * jitterFactor * (random * 2 - 1)};
   * {@code delay = max(0, min(base + jitter, maxDelay))}.
   *
   * @param attempt the number of failures so far (1-based)
   * @param config the retry settings
   * @param random uniform random numbers in {@code [0, 1)}
   * @return the delay in milliseconds (not rounded)
   */
  public static double calculateRetryDelay(int attempt, RetryConfig config, DoubleSupplier random) {
    double baseDelay = Math.min(config.initialDelay * Math.pow(config.backoffFactor, attempt - 1), config.maxDelay);
    double jitter = baseDelay * config.jitterFactor * (random.getAsDouble() * 2 - 1);
    return Math.max(0, Math.min(baseDelay + jitter, config.maxDelay));
  }

  /**
   * @param error a failure
   * @param config the retry settings
   * @return whether {@link #retryWithBackoff} retries it
   */
  public static boolean shouldRetryError(Throwable error, RetryConfig config) {
    String name = BoosterErrors.errorName(error);
    if (config.nonRetryableErrors != null && config.nonRetryableErrors.contains(name)) {
      return false;
    }
    if (Boolean.FALSE.equals(config.retryAllErrors)) {
      return config.retryableErrors != null && config.retryableErrors.contains(name);
    }
    return true;
  }
}
