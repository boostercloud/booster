package com.boostercloud.framework.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.RetryConfig;
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code packages/framework-common-helpers/test/retrier.test.ts}. */
class RetrierTest {

  static class ErrorToRetry extends RuntimeException {
    ErrorToRetry(String message) {
      super(message);
    }
  }

  static class SubErrorToRetry extends ErrorToRetry {
    SubErrorToRetry(String message) {
      super(message);
    }
  }

  @Nested
  class TheRetryIfErrorMethod {
    @Test
    void returnsTheResultAndDoesNotRetryIfThereIsNoError() {
      AtomicInteger retries = new AtomicInteger();
      String returnedValue = Retrier.retryIfError(tryNumber -> {
        retries.incrementAndGet();
        return "returned value";
      }, ErrorToRetry.class);
      assertThat(returnedValue).isEqualTo("returned value");
      assertThat(retries).hasValue(1);
    }

    @Test
    void rethrowsANonExpectedErrorAndDoesNotRetry() {
      AtomicInteger retries = new AtomicInteger();
      IllegalStateException unexpected = new IllegalStateException("unexpected error");
      assertThatThrownBy(() -> Retrier.retryIfError(tryNumber -> {
        retries.incrementAndGet();
        throw unexpected;
      }, ErrorToRetry.class)).isSameAs(unexpected);
      assertThat(retries).hasValue(1);
    }

    @Test
    void retries5TimesIfTheExpectedErrorHappens4Times() {
      AtomicInteger retries = new AtomicInteger();
      List<Integer> tryNumbers = new ArrayList<>();
      String result = Retrier.retryIfError(tryNumber -> {
        tryNumbers.add(tryNumber);
        if (retries.incrementAndGet() <= 4) {
          throw new SubErrorToRetry("expected error");
        }
        return "success";
      }, ErrorToRetry.class);
      assertThat(result).isEqualTo("success");
      assertThat(retries).hasValue(5);
      assertThat(tryNumbers).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void throwsAfterMaxRetriesIsReachedWithTheExpectedErrorHappening() {
      int maxRetries = 20;
      AtomicInteger retries = new AtomicInteger();
      assertThatThrownBy(() -> Retrier.retryIfError(tryNumber -> {
        retries.incrementAndGet();
        throw new ErrorToRetry("expected error");
      }, ErrorToRetry.class, null, maxRetries))
          .hasMessageContaining("Reached the maximum number of retries")
          .hasMessage("[retryIfError] Reached the maximum number of retries (20), but still getting the following error: "
              + "ErrorToRetry: expected error")
          .hasCauseInstanceOf(ErrorToRetry.class);
      assertThat(retries).hasValue(maxRetries);
    }

    @Test
    void neverRunsTheLogicWithoutAttempts() {
      AtomicInteger retries = new AtomicInteger();
      assertThatThrownBy(() -> Retrier.retryIfError(tryNumber -> retries.incrementAndGet(), ErrorToRetry.class, null, 0))
          .hasMessageEndingWith("(0), but still getting the following error: undefined");
      assertThat(retries).hasValue(0);
    }

    @Test
    void logsEveryAttempt() {
      Logger logger = mock(Logger.class);
      AtomicInteger retries = new AtomicInteger();
      Retrier.retryIfError(tryNumber -> {
        if (retries.incrementAndGet() == 1) {
          throw new OptimisticConcurrencyUnexpectedVersionError("conflict");
        }
        return "ok";
      }, OptimisticConcurrencyUnexpectedVersionError.class, logger);
      verify(logger).debug("[retryIfError] Try number 1");
      verify(logger).debug("[retryIfError] Try number 2");
      verify(logger).debug("[retryIfError] Succeeded after 2 retries");
    }
  }

  @Nested
  class TheRetryWithBackoffMethod {
    private final List<Long> sleeps = new ArrayList<>();
    private final Retrier.Sleeper fakeSleeper = sleeps::add;

    private RetryConfig defaultConfig() {
      return new RetryConfig(3, 100, 1000, 2, 0.1);
    }

    private <T> T retry(java.util.function.Supplier<T> logic, RetryConfig config, Logger logger) {
      return Retrier.retryWithBackoff(logic, config, logger, fakeSleeper, () -> 0.5);
    }

    @Test
    void returnsTheResultAndDoesNotRetryIfThereIsNoError() {
      AtomicInteger retries = new AtomicInteger();
      String returnedValue = Retrier.retryWithBackoff(() -> {
        retries.incrementAndGet();
        return "returned value";
      }, defaultConfig());
      assertThat(returnedValue).isEqualTo("returned value");
      assertThat(retries).hasValue(1);
    }

    @Test
    void retriesUntilSuccessWithinMaxRetries() {
      AtomicInteger retries = new AtomicInteger();
      String result = retry(() -> {
        if (retries.incrementAndGet() <= 2) {
          throw new IllegalStateException("temporary error");
        }
        return "success";
      }, defaultConfig(), null);
      assertThat(result).isEqualTo("success");
      assertThat(retries).hasValue(3);
      assertThat(sleeps).containsExactly(100L, 200L);
    }

    @Test
    void reallySleepsWithTheDefaultSleeper() {
      AtomicInteger retries = new AtomicInteger();
      long start = System.nanoTime();
      Retrier.retryWithBackoff(() -> {
        if (retries.incrementAndGet() == 1) {
          throw new IllegalStateException("temporary error");
        }
        return "success";
      }, new RetryConfig(3, 50, 1000, 2, 0));
      assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(45);
    }

    @Test
    void throwsAfterMaxRetriesIsReached() {
      AtomicInteger retries = new AtomicInteger();
      IllegalStateException persistent = new IllegalStateException("persistent error");
      assertThatThrownBy(() -> retry(() -> {
        retries.incrementAndGet();
        throw persistent;
      }, defaultConfig(), null)).isSameAs(persistent).hasMessage("persistent error");
      assertThat(retries).hasValue(defaultConfig().maxRetries);
      assertThat(sleeps).hasSize(2);
    }

    @Test
    void respectsTheMaxDelayCap() {
      RetryConfig config = defaultConfig();
      config.initialDelay = 1000;
      config.maxDelay = 2000;
      assertThat(Retrier.calculateRetryDelay(1, config)).isLessThan(config.maxDelay);
      assertThat(Retrier.calculateRetryDelay(2, config)).isLessThanOrEqualTo(config.maxDelay);
      assertThat(Retrier.calculateRetryDelay(3, config)).isLessThanOrEqualTo(config.maxDelay);
    }

    @Test
    void includesJitterInTheDelayCalculation() {
      RetryConfig config = defaultConfig();
      config.jitterFactor = 0.5;
      Set<Double> delays = new HashSet<>();
      for (int attempt = 1; attempt <= 10; attempt++) {
        delays.add(Retrier.calculateRetryDelay(attempt, config));
      }
      assertThat(delays.size()).isGreaterThan(1);
    }

    @Test
    void computesTheExponentialBaseAndClipsTheJitter() {
      RetryConfig config = RetryConfig.eventStoreDefaults();
      assertThat(Retrier.calculateRetryDelay(1, config, () -> 0.5)).isEqualTo(1000);
      assertThat(Retrier.calculateRetryDelay(2, config, () -> 0.5)).isEqualTo(2000);
      assertThat(Retrier.calculateRetryDelay(6, config, () -> 0.5)).isEqualTo(30000);
      assertThat(Retrier.calculateRetryDelay(1, config, () -> 0.0)).isEqualTo(900);
      assertThat(Retrier.calculateRetryDelay(1, config, () -> 0.99)).isCloseTo(1098, org.assertj.core.data.Offset.offset(0.001));
      assertThat(Retrier.calculateRetryDelay(9, config, () -> 0.99)).isEqualTo(30000);
      Random random = new Random(1);
      for (int i = 0; i < 100; i++) {
        double delay = Retrier.calculateRetryDelay(8, config, random::nextDouble);
        assertThat(delay).isBetween(27000.0, 30000.0);
      }
    }

    @Test
    void logsRetryAttemptsWhenLoggerIsProvided() {
      Logger logger = mock(Logger.class);
      AtomicInteger retries = new AtomicInteger();
      retry(() -> {
        if (retries.incrementAndGet() <= 2) {
          throw new IllegalStateException("temporary error");
        }
        return "success";
      }, defaultConfig(), logger);
      verify(logger, times(2)).debug(anyString());
      verify(logger, never()).error(any(), any());
      verify(logger).debug("[retryWithBackoff] Attempt 1 failed, retrying in 100ms...");
      verify(logger).debug("[retryWithBackoff] Attempt 2 failed, retrying in 200ms...");
    }

    @Test
    void logsTheFinalFailureWithTheError() {
      Logger logger = mock(Logger.class);
      IllegalStateException persistent = new IllegalStateException("persistent error");
      assertThatThrownBy(() -> retry(() -> {
        throw persistent;
      }, defaultConfig(), logger)).isSameAs(persistent);
      verify(logger).error("[retryWithBackoff] Failed after 3 attempts", persistent);
    }

    @Test
    void refusesConfigurationsWithoutAttempts() {
      RetryConfig config = defaultConfig();
      config.maxRetries = 0;
      AtomicInteger retries = new AtomicInteger();
      assertThatThrownBy(() -> retry(retries::incrementAndGet, config, null)).isInstanceOf(IllegalArgumentException.class);
      assertThat(retries).hasValue(0);
    }

    @Nested
    class ErrorFiltering {
      static class RetryableError extends RuntimeException {
        RetryableError(String message) {
          super(message);
        }
      }

      static class NonRetryableError extends RuntimeException {
        NonRetryableError(String message) {
          super(message);
        }
      }

      static class OtherError extends RuntimeException {
        OtherError(String message) {
          super(message);
        }
      }

      @Test
      void retriesAllErrorsWhenRetryAllErrorsIsTrue() {
        RetryConfig config = defaultConfig();
        config.retryAllErrors = true;
        AtomicInteger retries = new AtomicInteger();
        String result = retry(() -> {
          if (retries.incrementAndGet() <= 2) {
            throw new OtherError("some error");
          }
          return "success";
        }, config, null);
        assertThat(result).isEqualTo("success");
        assertThat(retries).hasValue(3);
      }

      @Test
      void retriesAllErrorsWhenRetryAllErrorsIsUndefined() {
        RetryConfig config = defaultConfig();
        config.retryAllErrors = null;
        AtomicInteger retries = new AtomicInteger();
        String result = retry(() -> {
          if (retries.incrementAndGet() <= 2) {
            throw new OtherError("some error");
          }
          return "success";
        }, config, null);
        assertThat(result).isEqualTo("success");
        assertThat(retries).hasValue(3);
      }

      @Test
      void onlyRetriesErrorsInRetryableErrorsWhenRetryAllErrorsIsFalse() {
        RetryConfig config = defaultConfig();
        config.retryAllErrors = false;
        config.retryableErrors = List.of("RetryableError");
        AtomicInteger retries = new AtomicInteger();
        String result = retry(() -> {
          if (retries.incrementAndGet() <= 2) {
            throw new RetryableError("retryable error");
          }
          return "success";
        }, config, null);
        assertThat(result).isEqualTo("success");
        assertThat(retries).hasValue(3);
      }

      @Test
      void doesNotRetryErrorsNotInRetryableErrorsWhenRetryAllErrorsIsFalse() {
        RetryConfig config = defaultConfig();
        config.retryAllErrors = false;
        config.retryableErrors = List.of("RetryableError");
        AtomicInteger retries = new AtomicInteger();
        assertThatThrownBy(() -> retry(() -> {
          retries.incrementAndGet();
          throw new OtherError("non-retryable error");
        }, config, null)).hasMessage("non-retryable error");
        assertThat(retries).hasValue(1);
      }

      @Test
      void doesNotRetryAnythingWhenRetryAllErrorsIsFalseWithoutList() {
        RetryConfig config = defaultConfig();
        config.retryAllErrors = false;
        AtomicInteger retries = new AtomicInteger();
        assertThatThrownBy(() -> retry(() -> {
          retries.incrementAndGet();
          throw new RetryableError("error");
        }, config, null)).isInstanceOf(RetryableError.class);
        assertThat(retries).hasValue(1);
      }

      @Test
      void neverRetriesErrorsInNonRetryableErrorsRegardlessOfOtherSettings() {
        RetryConfig config = defaultConfig();
        config.retryAllErrors = true;
        config.nonRetryableErrors = List.of("NonRetryableError");
        AtomicInteger retries = new AtomicInteger();
        assertThatThrownBy(() -> retry(() -> {
          retries.incrementAndGet();
          throw new NonRetryableError("non-retryable error");
        }, config, null)).hasMessage("non-retryable error");
        assertThat(retries).hasValue(1);
      }

      @Test
      void nonRetryableErrorsTakePrecedenceOverRetryableErrors() {
        RetryConfig config = defaultConfig();
        config.retryAllErrors = false;
        config.retryableErrors = List.of("RetryableError", "NonRetryableError");
        config.nonRetryableErrors = List.of("NonRetryableError");
        AtomicInteger retries = new AtomicInteger();
        assertThatThrownBy(() -> retry(() -> {
          if (retries.incrementAndGet() <= 2) {
            throw new NonRetryableError("non-retryable error");
          }
          return "success";
        }, config, null)).hasMessage("non-retryable error");
        assertThat(retries).hasValue(1);
      }

      @Test
      void logsWhenSkippingRetryDueToErrorType() {
        Logger logger = mock(Logger.class);
        RetryConfig config = defaultConfig();
        config.retryAllErrors = false;
        config.retryableErrors = List.of("RetryableError");
        assertThatThrownBy(() -> retry(() -> {
          throw new OtherError("non-retryable error");
        }, config, logger)).hasMessage("non-retryable error");
        verify(logger).debug("[retryWithBackoff] Error OtherError is not retryable, failing immediately");
      }

      @Test
      void matchesBoosterErrorsByTheirClassName() {
        RetryConfig config = defaultConfig();
        config.nonRetryableErrors = List.of("OptimisticConcurrencyUnexpectedVersionError");
        AtomicInteger retries = new AtomicInteger();
        assertThatThrownBy(() -> retry(() -> {
          retries.incrementAndGet();
          throw new OptimisticConcurrencyUnexpectedVersionError("conflict");
        }, config, null)).isInstanceOf(OptimisticConcurrencyUnexpectedVersionError.class);
        assertThat(retries).hasValue(1);
      }
    }
  }
}
