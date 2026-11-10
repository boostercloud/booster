package com.boostercloud.framework.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Joining concurrent work, the Java form of {@code Promise.allSettled}/{@code Promise.all}. Port of
 * {@code promises.ts} ({@code Promises.allSettledAndFulfilled}) plus the other combinators Booster needs.
 *
 * <p>JavaScript promises are already running when they are passed; the {@code allSettled*}/{@code all} methods
 * likewise join futures that were already started, and the {@code run*}/{@code map*}/{@code forEach*} methods start
 * each task on its own virtual thread first. The failure semantics are those of TypeScript:
 *
 * <ul>
 *   <li>{@code *SettledAndFulfilled}: wait for <b>every</b> task (never short-circuit), then throw a
 *       {@link PromisesError} with all failures in input order if any failed; otherwise return the values in input
 *       order;
 *   <li>{@code all}/{@code runAll} ({@code Promise.all}): throw the first failure as soon as it happens (the other
 *       tasks keep running), otherwise return the values in input order.
 * </ul>
 *
 * Tasks run truly in parallel (JavaScript interleaves them on one thread); code that shares mutable state between
 * tasks must synchronise it.
 */
public final class Promises {

  private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

  private Promises() {}

  /**
   * Starts a task on a new virtual thread, like calling an {@code async} function.
   *
   * @param task the task
   * @param <T> the result type
   * @return its future
   */
  public static <T> CompletableFuture<T> start(Callable<? extends T> task) {
    Objects.requireNonNull(task, "task");
    CompletableFuture<T> future = new CompletableFuture<>();
    Thread.ofVirtual().name("booster-task-" + THREAD_COUNTER.incrementAndGet()).start(() -> {
      try {
        future.complete(task.call());
      } catch (Throwable error) {
        future.completeExceptionally(error);
      }
    });
    return future;
  }

  /**
   * {@code Promises.allSettledAndFulfilled(promises)}: waits for every future, then throws a {@link PromisesError}
   * if any failed.
   *
   * @param futures already started tasks
   * @param <T> the value type
   * @return the values, in input order
   * @throws PromisesError when at least one future failed
   */
  public static <T> List<T> allSettledAndFulfilled(Collection<? extends Future<? extends T>> futures) {
    List<SettledResult<T>> results = allSettled(futures);
    List<Throwable> failed = new ArrayList<>();
    List<T> values = new ArrayList<>(results.size());
    for (SettledResult<T> result : results) {
      switch (result) {
        case SettledResult.Fulfilled<T> fulfilled -> values.add(fulfilled.value());
        case SettledResult.Rejected<T> rejected -> failed.add(rejected.reason());
      }
    }
    if (!failed.isEmpty()) {
      throw new PromisesError(failed);
    }
    return values;
  }

  /**
   * Starts every task on a virtual thread and joins them with {@link #allSettledAndFulfilled(Collection)}.
   *
   * @param tasks the tasks
   * @param <T> the value type
   * @return the values, in input order
   * @throws PromisesError when at least one task failed
   */
  public static <T> List<T> runAllSettledAndFulfilled(Collection<? extends Callable<? extends T>> tasks) {
    return allSettledAndFulfilled(startAll(tasks));
  }

  /**
   * {@code Promises.allSettledAndFulfilled(items.map(async (item) => mapper(item)))}.
   *
   * @param items the items
   * @param mapper the work for one item
   * @param <E> the item type
   * @param <R> the result type
   * @return the results, in item order
   * @throws PromisesError when the mapper failed for at least one item
   */
  public static <E, R> List<R> mapAllSettledAndFulfilled(Collection<? extends E> items, Function<? super E, ? extends R> mapper) {
    List<Callable<R>> tasks = new ArrayList<>(items.size());
    for (E item : items) {
      tasks.add(() -> mapper.apply(item));
    }
    return runAllSettledAndFulfilled(tasks);
  }

  /**
   * {@code Promises.allSettledAndFulfilled(items.map(async (item) => { action(item) }))}.
   *
   * @param items the items
   * @param action the work for one item
   * @param <E> the item type
   * @throws PromisesError when the action failed for at least one item
   */
  public static <E> void forEachAllSettledAndFulfilled(Collection<? extends E> items, Consumer<? super E> action) {
    mapAllSettledAndFulfilled(items, item -> {
      action.accept(item);
      return null;
    });
  }

  /**
   * {@code Promise.allSettled(promises)}: waits for every future and reports each outcome; never throws because of
   * a failed task.
   *
   * @param futures already started tasks
   * @param <T> the value type
   * @return the outcomes, in input order
   */
  public static <T> List<SettledResult<T>> allSettled(Collection<? extends Future<? extends T>> futures) {
    List<SettledResult<T>> results = new ArrayList<>(futures.size());
    for (Future<? extends T> future : futures) {
      results.add(settle(future));
    }
    return results;
  }

  /**
   * Starts every task on a virtual thread and reports each outcome.
   *
   * @param tasks the tasks
   * @param <T> the value type
   * @return the outcomes, in input order
   */
  public static <T> List<SettledResult<T>> runAllSettled(Collection<? extends Callable<? extends T>> tasks) {
    return allSettled(startAll(tasks));
  }

  /**
   * {@code Promise.all(promises)}: the values in input order, or the first failure (in completion order) as soon as
   * it happens; the other tasks are not cancelled.
   *
   * @param futures already started tasks
   * @param <T> the value type
   * @return the values, in input order
   */
  public static <T> List<T> all(Collection<? extends CompletableFuture<? extends T>> futures) {
    CompletableFuture<?>[] array = futures.toArray(CompletableFuture<?>[]::new);
    CompletableFuture<Object> firstFailure = new CompletableFuture<>();
    for (CompletableFuture<?> future : array) {
      future.whenComplete((value, error) -> {
        if (error != null) {
          firstFailure.completeExceptionally(unwrap(error));
        }
      });
    }
    try {
      CompletableFuture.anyOf(CompletableFuture.allOf(array), firstFailure).join();
    } catch (CompletionException | CancellationException e) {
      throw Exceptions.sneakyThrow(unwrap(e));
    }
    List<T> values = new ArrayList<>(array.length);
    for (CompletableFuture<? extends T> future : futures) {
      values.add(future.join());
    }
    return values;
  }

  /**
   * Starts every task on a virtual thread and joins them with {@link #all(Collection)}.
   *
   * @param tasks the tasks
   * @param <T> the value type
   * @return the values, in input order
   */
  public static <T> List<T> runAll(Collection<? extends Callable<? extends T>> tasks) {
    return all(startAll(tasks));
  }

  private static <T> List<CompletableFuture<T>> startAll(Collection<? extends Callable<? extends T>> tasks) {
    List<CompletableFuture<T>> futures = new ArrayList<>(tasks.size());
    for (Callable<? extends T> task : tasks) {
      futures.add(start(task));
    }
    return futures;
  }

  private static <T> SettledResult<T> settle(Future<? extends T> future) {
    try {
      return new SettledResult.Fulfilled<>(future.get());
    } catch (ExecutionException e) {
      return new SettledResult.Rejected<>(unwrap(e));
    } catch (CancellationException e) {
      return new SettledResult.Rejected<>(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CancellationException("Interrupted while waiting for the tasks to finish");
    }
  }

  private static Throwable unwrap(Throwable error) {
    Throwable current = error;
    while ((current instanceof ExecutionException || current instanceof CompletionException) && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }
}
