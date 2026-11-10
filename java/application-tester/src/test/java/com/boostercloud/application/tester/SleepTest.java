package com.boostercloud.application.tester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.types.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SleepTest {

  /** A fake clock advanced by the fake sleeper. */
  private final AtomicLong now = new AtomicLong(1_000_000);
  private final List<Long> sleeps = new ArrayList<>();
  private final Retrier.Sleeper sleeper = millis -> {
    sleeps.add(millis);
    now.addAndGet(millis);
  };

  private <T> T waitFor(Supplier<T> tryFunction, java.util.function.Function<? super T, ?> check, long delay, long timeout) {
    return Sleep.waitForIt(tryFunction, check, delay, timeout, Logger.NOOP, now::get, sleeper);
  }

  @AfterEach
  void restoreLogger() {
    Sleep.resetLogger();
  }

  @Test
  @DisplayName("returns the first result when the check returns true at once, without sleeping")
  void immediateMatch() {
    AtomicInteger calls = new AtomicInteger();
    String result = waitFor(() -> "value-" + calls.incrementAndGet(), value -> true, 1000, 600000);
    assertThat(result).isEqualTo("value-1");
    assertThat(calls).hasValue(1);
    assertThat(sleeps).isEmpty();
  }

  @Test
  @DisplayName("keeps trying every trialDelayMs until the check returns true")
  void pollsUntilMatch() {
    AtomicInteger calls = new AtomicInteger();
    int result = waitFor(calls::incrementAndGet, count -> count == 4, 1000, 600000);
    assertThat(result).isEqualTo(4);
    assertThat(sleeps).containsExactly(1000L, 1000L, 1000L);
  }

  @Test
  @DisplayName("a string result means keep waiting, and it is appended to the timeout error")
  void stringKeepsWaitingAndExplainsTheTimeout() {
    AtomicInteger calls = new AtomicInteger();
    assertThatThrownBy(() -> waitFor(calls::incrementAndGet, count -> "only " + count + " so far", 1000, 2500))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("[waitForIt] Timeout reached. only 4 so far");
    // attempts at t=0, 1000, 2000 and 3000 (> 2500): the timeout is checked after each failed attempt
    assertThat(calls).hasValue(4);
    assertThat(sleeps).containsExactly(1000L, 1000L, 1000L);
  }

  @Test
  @DisplayName("an empty string keeps waiting and adds no suffix to the timeout error")
  void emptyString() {
    assertThatThrownBy(() -> waitFor(() -> 1, value -> "", 10, 15)).hasMessage("[waitForIt] Timeout reached");
  }

  @Test
  @DisplayName("false keeps waiting and times out with the plain message")
  void booleanTimeout() {
    assertThatThrownBy(() -> waitFor(() -> 1, value -> false, 100, 250))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("[waitForIt] Timeout reached");
  }

  @Test
  @DisplayName("non-boolean values follow JavaScript truthiness for the suffix")
  void otherValues() {
    assertThatThrownBy(() -> waitFor(() -> 1, value -> null, 10, 5)).hasMessage("[waitForIt] Timeout reached");
    assertThatThrownBy(() -> waitFor(() -> 1, value -> 0, 10, 5)).hasMessage("[waitForIt] Timeout reached");
    assertThatThrownBy(() -> waitFor(() -> 1, value -> 2.5, 10, 5)).hasMessage("[waitForIt] Timeout reached. 2.5");
    assertThatThrownBy(() -> waitFor(() -> 1, value -> 3.0, 10, 5)).hasMessage("[waitForIt] Timeout reached. 3");
  }

  @Test
  @DisplayName("a string and true can be mixed: the string means not yet")
  void mixedResults() {
    AtomicInteger calls = new AtomicInteger();
    int result = waitFor(calls::incrementAndGet, count -> count >= 3 ? true : "count is " + count, 1000, 600000);
    assertThat(result).isEqualTo(3);
  }

  @Test
  @DisplayName("does not time out while the elapsed time equals the timeout")
  void timeoutIsStrict() {
    AtomicInteger calls = new AtomicInteger();
    int result = waitFor(calls::incrementAndGet, count -> count == 3, 1000, 2000);
    assertThat(result).isEqualTo(3);
  }

  @Test
  @DisplayName("an exception of the try function propagates immediately")
  void tryFunctionErrors() {
    AtomicInteger calls = new AtomicInteger();
    assertThatThrownBy(() -> waitFor(() -> {
          calls.incrementAndGet();
          throw new IllegalArgumentException("boom");
        }, value -> true, 1000, 600000))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("boom");
    assertThat(calls).hasValue(1);
    assertThat(sleeps).isEmpty();
  }

  @Test
  @DisplayName("logs start, one dot per attempt and match at debug level")
  void logs() {
    Logger logger = mock(Logger.class);
    AtomicInteger calls = new AtomicInteger();
    Sleep.waitForIt(calls::incrementAndGet, count -> count == 2, 1000, 600000, logger, now::get, sleeper);
    InOrder order = inOrder(logger);
    order.verify(logger).debug("[waitForIt] start");
    order.verify(logger, org.mockito.Mockito.times(2)).debug(".");
    order.verify(logger).debug("[waitForIt] match!");
    order.verifyNoMoreInteractions();
  }

  @Test
  @DisplayName("defaults to a 1 s delay and a 10 min timeout, with the real clock")
  void defaults() {
    assertThat(Sleep.DEFAULT_TRIAL_DELAY_MS).isEqualTo(1000);
    assertThat(Sleep.DEFAULT_TIMEOUT_MS).isEqualTo(600000);
    Sleep.useLogger(Logger.NOOP);
    AtomicInteger calls = new AtomicInteger();
    long start = System.nanoTime();
    int result = Sleep.waitForIt(calls::incrementAndGet, count -> count == 2);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
    assertThat(result).isEqualTo(2);
    assertThat(elapsedMs).isGreaterThanOrEqualTo(990);
  }

  @Test
  @DisplayName("times out for real with short timings")
  void realTimeout() {
    Sleep.useLogger(Logger.NOOP);
    assertThatThrownBy(() -> Sleep.waitForIt(() -> "x", value -> "still " + value, 10, 50))
        .hasMessage("[waitForIt] Timeout reached. still x");
  }

  @Test
  @DisplayName("sleep blocks and an interruption cancels it")
  void sleep() {
    long start = System.nanoTime();
    Sleep.sleep(20);
    assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(19);
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> Sleep.sleep(1000)).isInstanceOf(CancellationException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}
