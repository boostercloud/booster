package com.boostercloud.framework.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code packages/framework-common-helpers/test/promises.test.ts}, plus the other combinators. */
class PromisesTest {

  private static CompletableFuture<Object> resolved() {
    return CompletableFuture.completedFuture(null);
  }

  private static CompletableFuture<Object> rejected(String reason) {
    return CompletableFuture.failedFuture(new RuntimeException(reason));
  }

  @Nested
  class TheAllSettledAndFulfilledMethod {
    @Test
    void doesNotThrowIfAllPromisesAreFulfilled() {
      assertThatCode(() -> Promises.allSettledAndFulfilled(List.of(resolved(), resolved(), resolved())))
          .doesNotThrowAnyException();
    }

    @Test
    void throwsWithAnArrayOfRejectedPromises() {
      List<CompletableFuture<Object>> promises =
          List.of(resolved(), rejected("rejection 1"), rejected("rejection 2"), resolved());
      assertThatThrownBy(() -> Promises.allSettledAndFulfilled(promises))
          .isInstanceOf(PromisesError.class)
          .hasMessageMatching(".*rejection 1.*rejection 2.*")
          .hasMessage("RuntimeException: rejection 1. RuntimeException: rejection 2")
          .satisfies(error -> {
            PromisesError promisesError = (PromisesError) error;
            assertThat(promisesError.failedReasons()).extracting(Throwable::getMessage)
                .containsExactly("rejection 1", "rejection 2");
            assertThat(promisesError.getCause()).hasMessage("rejection 1");
            assertThat(promisesError.getSuppressed()).hasSize(1);
          });
    }

    @Test
    void itWaitsForAllThePromisesToFinishEvenIfOneOfThemThrowsEarly() {
      AtomicBoolean successfulPromise1Finished = new AtomicBoolean();
      AtomicBoolean successfulPromise2Finished = new AtomicBoolean();
      List<CompletableFuture<Object>> promises = List.of(
          rejected("rejected"),
          Promises.start(() -> {
            Thread.sleep(100);
            successfulPromise1Finished.set(true);
            return null;
          }),
          Promises.start(() -> {
            Thread.sleep(500);
            successfulPromise2Finished.set(true);
            return null;
          }));
      assertThatThrownBy(() -> Promises.allSettledAndFulfilled(promises))
          .isInstanceOf(PromisesError.class)
          .hasMessageContaining("rejected");
      assertThat(successfulPromise1Finished).isTrue();
      assertThat(successfulPromise2Finished).isTrue();
    }

    @Test
    void returnsTheValuesInInputOrder() {
      List<Object> values = Promises.allSettledAndFulfilled(List.of(
          Promises.start(() -> {
            Thread.sleep(50);
            return "slow";
          }),
          CompletableFuture.completedFuture("fast")));
      assertThat(values).containsExactly("slow", "fast");
    }
  }

  @Nested
  class TheTaskRunners {
    @Test
    void runTasksConcurrentlyOnVirtualThreads() throws InterruptedException {
      CountDownLatch bothStarted = new CountDownLatch(2);
      List<Callable<Boolean>> tasks = List.of(
          () -> {
            bothStarted.countDown();
            return bothStarted.await(5, TimeUnit.SECONDS) && Thread.currentThread().isVirtual();
          },
          () -> {
            bothStarted.countDown();
            return bothStarted.await(5, TimeUnit.SECONDS) && Thread.currentThread().isVirtual();
          });
      assertThat(Promises.runAllSettledAndFulfilled(tasks)).containsExactly(true, true);
    }

    @Test
    void mapAndForEachKeepItemOrderAndAggregateFailures() {
      assertThat(Promises.mapAllSettledAndFulfilled(List.of(1, 2, 3), item -> item * 10)).containsExactly(10, 20, 30);
      List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
      assertThatThrownBy(() -> Promises.forEachAllSettledAndFulfilled(List.of(1, 2, 3), item -> {
        seen.add(item);
        if (item != 2) {
          throw new IllegalStateException("failed " + item);
        }
      })).isInstanceOf(PromisesError.class).hasMessage("IllegalStateException: failed 1. IllegalStateException: failed 3");
      assertThat(seen).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void reportEveryOutcomeWithAllSettled() {
      List<SettledResult<Object>> results = Promises.runAllSettled(List.<Callable<Object>>of(
          () -> "ok",
          () -> {
            throw new IllegalArgumentException("bad");
          }));
      assertThat(results.get(0)).isEqualTo(new SettledResult.Fulfilled<>("ok"));
      assertThat(results.get(0).status()).isEqualTo("fulfilled");
      assertThat(PromiseUtils.isFulfilled(results.get(0))).isTrue();
      assertThat(PromiseUtils.isRejected(results.get(1))).isTrue();
      assertThat(((SettledResult.Rejected<Object>) results.get(1)).reason()).hasMessage("bad");
      assertThat(results.get(1).status()).isEqualTo("rejected");
    }

    @Test
    void failFastWithAll() {
      CompletableFuture<Object> neverEnding = new CompletableFuture<>();
      long start = System.nanoTime();
      IllegalStateException failure = new IllegalStateException("first");
      assertThatThrownBy(() -> Promises.all(List.of(neverEnding, CompletableFuture.failedFuture(failure))))
          .isSameAs(failure);
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
      assertThat(neverEnding).isNotDone();
      assertThat(Promises.runAll(List.<Callable<Integer>>of(() -> 1, () -> 2))).containsExactly(1, 2);
    }
  }
}
