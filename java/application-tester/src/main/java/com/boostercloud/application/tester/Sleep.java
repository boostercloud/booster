package com.boostercloud.application.tester;

import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.ConsoleLogger;
import com.boostercloud.framework.types.Logger;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The eventual-consistency primitives of the integration suite: {@link #sleep(long)} and the polling
 * {@link #waitForIt(Supplier, Function)}. Port of {@code framework-integration-tests/integration/helper/sleep.ts},
 * shipped here so every suite that uses {@link ApplicationTester} gets them.
 *
 * <p>{@code waitForIt} calls the try function, passes its result to the check function and:
 *
 * <ul>
 *   <li>returns the result when the check returns {@code true} ({@link Boolean#TRUE});
 *   <li>keeps waiting when it returns {@code false}, or <b>any non-boolean value</b>: a {@link String} (or any other
 *       value) is a diagnostic that is appended to the timeout error, {@code "[waitForIt] Timeout reached. <value>"}
 *       ({@code null}, the empty string, {@code 0} and {@code NaN} add no suffix, like JavaScript falsy values);
 *   <li>after a failed attempt, throws {@code IllegalStateException("[waitForIt] Timeout reached")} (or the message
 *       with the diagnostic) when more than {@code timeoutMs} milliseconds have elapsed since the start; otherwise
 *       sleeps {@code trialDelayMs} and tries again.
 * </ul>
 *
 * The try function always runs at least once and an exception it throws propagates immediately (no retry). The
 * total time can exceed the timeout by one attempt plus one delay. Defaults: 1 s between attempts, 10 min timeout.
 *
 * <p>Like the TypeScript helper it logs {@code [waitForIt] start}, a {@code .} per attempt and
 * {@code [waitForIt] match!} at debug level, to {@link ConsoleLogger} by default ({@link #useLogger(Logger)}
 * changes it, e.g. to {@link Logger#NOOP}).
 *
 * <pre>{@code
 * long count = Sleep.waitForIt(counters::subscriptions, newCount -> newCount == original + 2);
 * GraphQLResponse cart = Sleep.waitForIt(
 *     () -> client.query(CART_QUERY, Map.of("cartId", id)),
 *     response -> response.get("CartReadModel") != null ? true : "cart " + id + " not projected yet");
 * }</pre>
 */
public final class Sleep {

  /** Default delay between attempts ({@code trialDelayMs = 1000}). */
  public static final long DEFAULT_TRIAL_DELAY_MS = 1000;

  /** Default timeout ({@code timeoutMs = 600000}, 10 minutes). */
  public static final long DEFAULT_TIMEOUT_MS = 600000;

  private static volatile Logger logger = ConsoleLogger.INSTANCE;

  private Sleep() {}

  /**
   * {@code sleep(ms)}: blocks the current thread.
   *
   * @param ms milliseconds to wait
   * @throws java.util.concurrent.CancellationException when the thread is interrupted (the interrupt flag is kept)
   */
  public static void sleep(long ms) {
    Retrier.Sleeper.THREAD_SLEEP.sleep(ms);
  }

  /**
   * Polls with the default delay (1 s) and timeout (10 min).
   *
   * @param tryFunction produces the value to check (TypeScript: an async function)
   * @param checkResult {@code true} to stop; {@code false} or a diagnostic (usually a {@link String}) to keep waiting
   * @param <T> the result type
   * @return the first result accepted by the check
   */
  public static <T> T waitForIt(Supplier<? extends T> tryFunction, Function<? super T, ?> checkResult) {
    return waitForIt(tryFunction, checkResult, DEFAULT_TRIAL_DELAY_MS, DEFAULT_TIMEOUT_MS);
  }

  /**
   * Polls with the given delay and the default timeout (10 min).
   *
   * @param tryFunction produces the value to check
   * @param checkResult {@code true} to stop; {@code false} or a diagnostic to keep waiting
   * @param trialDelayMs milliseconds between attempts
   * @param <T> the result type
   * @return the first result accepted by the check
   */
  public static <T> T waitForIt(
      Supplier<? extends T> tryFunction, Function<? super T, ?> checkResult, long trialDelayMs) {
    return waitForIt(tryFunction, checkResult, trialDelayMs, DEFAULT_TIMEOUT_MS);
  }

  /**
   * Polls until the check accepts a result or the timeout is reached.
   *
   * @param tryFunction produces the value to check
   * @param checkResult {@code true} to stop; {@code false} or a diagnostic to keep waiting
   * @param trialDelayMs milliseconds between attempts
   * @param timeoutMs milliseconds after which a failed attempt ends the wait with an error
   * @param <T> the result type
   * @return the first result accepted by the check
   * @throws IllegalStateException {@code [waitForIt] Timeout reached} (plus {@code . <diagnostic>})
   */
  public static <T> T waitForIt(
      Supplier<? extends T> tryFunction, Function<? super T, ?> checkResult, long trialDelayMs, long timeoutMs) {
    return waitForIt(
        tryFunction, checkResult, trialDelayMs, timeoutMs, logger, System::currentTimeMillis,
        Retrier.Sleeper.THREAD_SLEEP);
  }

  /** The loop, with the clock, sleeper and logger injectable for tests. */
  static <T> T waitForIt(
      Supplier<? extends T> tryFunction,
      Function<? super T, ?> checkResult,
      long trialDelayMs,
      long timeoutMs,
      Logger log,
      LongSupplier clock,
      Retrier.Sleeper sleeper) {
    Objects.requireNonNull(tryFunction, "tryFunction");
    Objects.requireNonNull(checkResult, "checkResult");
    log.debug("[waitForIt] start");
    long start = clock.getAsLong();
    while (true) {
      log.debug(".");
      T result = tryFunction.get();
      Object checkResultValue = checkResult.apply(result);
      boolean waitingResult = checkResultValue instanceof Boolean bool && bool;
      if (waitingResult) {
        log.debug("[waitForIt] match!");
        return result;
      }
      long elapsed = clock.getAsLong() - start;
      if (elapsed > timeoutMs) {
        if (checkResultValue instanceof Boolean) {
          throw new IllegalStateException("[waitForIt] Timeout reached");
        }
        String message = isTruthy(checkResultValue) ? ". " + jsString(checkResultValue) : "";
        throw new IllegalStateException("[waitForIt] Timeout reached" + message);
      }
      sleeper.sleep(trialDelayMs);
    }
  }

  /**
   * Changes the logger of the {@code [waitForIt]} debug lines for the whole JVM.
   *
   * @param newLogger the logger ({@link Logger#NOOP} silences them)
   */
  public static void useLogger(Logger newLogger) {
    logger = Objects.requireNonNull(newLogger, "newLogger");
  }

  /** Restores the default logger ({@link ConsoleLogger#INSTANCE}). */
  public static void resetLogger() {
    logger = ConsoleLogger.INSTANCE;
  }

  /** JavaScript truthiness of a raw value ({@code null}, {@code false}, {@code ""}, {@code 0}, {@code NaN} are falsy). */
  static boolean isTruthy(Object value) {
    return switch (value) {
      case null -> false;
      case Boolean bool -> bool;
      case String string -> !string.isEmpty();
      case Double number -> number != 0 && !number.isNaN();
      case Float number -> number != 0 && !number.isNaN();
      case Number number -> number.doubleValue() != 0;
      default -> true;
    };
  }

  /** JavaScript template-literal rendering of a check result. */
  private static String jsString(Object value) {
    return switch (value) {
      case Double number -> BoosterJson.formatNumber(number);
      case Float number -> BoosterJson.formatNumber(number.doubleValue());
      default -> String.valueOf(value);
    };
  }
}
