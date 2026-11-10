package com.boostercloud.application.tester.graphql;

import com.boostercloud.framework.types.ConsoleLogger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A JavaScript-like event loop: tasks run one at a time, in submission order, on a virtual thread that only lives
 * while there is work. The subscription client runs every socket event, timer and state change on its loop, so its
 * state machine is the single-threaded one of {@code subscriptions-transport-ws}; subscription observers are called
 * on the loop too. Idle loops hold no thread, so a client that is never closed does not keep the JVM alive.
 */
final class EventLoop {

  /** A cancellable timer ({@code clearTimeout}). */
  interface Timer {
    /** Cancels the timer; a cancelled timer never runs its task. */
    void cancel();
  }

  private static final ThreadLocal<EventLoop> CURRENT = new ThreadLocal<>();
  private static final ScheduledThreadPoolExecutor TIMERS = createTimers();

  private final String name;
  private final Deque<Runnable> tasks = new ArrayDeque<>();
  private boolean draining;

  /**
   * @param name the name of the loop thread
   */
  EventLoop(String name) {
    this.name = name;
  }

  /**
   * Queues a task ({@code setTimeout(task, 0)} / {@code process.nextTick}).
   *
   * @param task the task
   */
  void execute(Runnable task) {
    boolean start;
    synchronized (this) {
      tasks.add(task);
      start = !draining;
      draining = true;
    }
    if (start) {
      Thread.ofVirtual().name(name).start(this::drain);
    }
  }

  /**
   * @return whether the calling code runs on this loop
   */
  boolean inLoop() {
    return CURRENT.get() == this;
  }

  /**
   * Runs a computation on the loop and waits for it (inline when already on the loop).
   *
   * @param computation the computation
   * @param <T> the result type
   * @return its result
   */
  <T> T call(Supplier<T> computation) {
    if (inLoop()) {
      return computation.get();
    }
    CompletableFuture<T> result = new CompletableFuture<>();
    execute(() -> {
      try {
        result.complete(computation.get());
      } catch (Throwable error) {
        result.completeExceptionally(error);
      }
    });
    return await(result);
  }

  /**
   * Runs an action on the loop and waits for it (inline when already on the loop).
   *
   * @param action the action
   */
  void run(Runnable action) {
    call(() -> {
      action.run();
      return null;
    });
  }

  /**
   * {@code setTimeout(task, delayMs)}.
   *
   * @param task the task, run on the loop
   * @param delayMs the delay in milliseconds
   * @return the timer
   */
  Timer schedule(Runnable task, long delayMs) {
    CancellableTask cancellable = new CancellableTask(task);
    ScheduledFuture<?> future =
        TIMERS.schedule(() -> execute(cancellable), Math.max(0, delayMs), TimeUnit.MILLISECONDS);
    return () -> {
      cancellable.cancelled = true;
      future.cancel(false);
    };
  }

  /**
   * {@code setInterval(task, periodMs)}.
   *
   * @param task the task, run on the loop
   * @param periodMs the period in milliseconds
   * @return the timer
   */
  Timer scheduleAtFixedRate(Runnable task, long periodMs) {
    CancellableTask cancellable = new CancellableTask(task);
    long period = Math.max(1, periodMs);
    ScheduledFuture<?> future =
        TIMERS.scheduleAtFixedRate(() -> execute(cancellable), period, period, TimeUnit.MILLISECONDS);
    return () -> {
      cancellable.cancelled = true;
      future.cancel(false);
    };
  }

  /**
   * Waits for a future, rethrowing its failure unwrapped.
   *
   * @param future the future
   * @param <T> the result type
   * @return the result
   */
  static <T> T await(CompletableFuture<T> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CancellationException("Interrupted while waiting");
    } catch (ExecutionException e) {
      throw rethrow(e.getCause());
    }
  }

  /**
   * @param error a failure
   * @return the failure as an unchecked exception (to be thrown by the caller)
   */
  static RuntimeException rethrow(Throwable error) {
    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    if (cause instanceof RuntimeException runtime) {
      return runtime;
    }
    if (cause instanceof Error fatal) {
      throw fatal;
    }
    return new CompletionException(cause);
  }

  private void drain() {
    CURRENT.set(this);
    try {
      while (true) {
        Runnable task;
        synchronized (this) {
          task = tasks.poll();
          if (task == null) {
            draining = false;
            return;
          }
        }
        try {
          task.run();
        } catch (Throwable error) {
          reportUncaught(error);
        }
      }
    } finally {
      CURRENT.remove();
    }
  }

  /** The equivalent of an uncaught exception in a Node.js callback: printed to stderr. */
  static void reportUncaught(Throwable error) {
    ConsoleLogger.INSTANCE.error("Uncaught error in a GraphQL client callback:", error);
  }

  private static final class CancellableTask implements Runnable {
    private final Runnable task;
    private volatile boolean cancelled;

    CancellableTask(Runnable task) {
      this.task = task;
    }

    @Override
    public void run() {
      if (!cancelled) {
        task.run();
      }
    }
  }

  private static ScheduledThreadPoolExecutor createTimers() {
    AtomicInteger counter = new AtomicInteger();
    ThreadFactory factory = runnable -> {
      Thread thread = new Thread(runnable, "graphql-client-timers-" + counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
    ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(1, factory);
    timers.setRemoveOnCancelPolicy(true);
    timers.setKeepAliveTime(1, TimeUnit.SECONDS);
    timers.allowCoreThreadTimeOut(true);
    return timers;
  }
}
