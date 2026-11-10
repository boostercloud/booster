package com.boostercloud.cli.integration.helper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Every process the suite starts, so that none outlives it. A tracked process remembers the descendants seen while it
 * ran (the CLI JVM starts Maven and the application JVM; once a parent dies its children are re-parented and no
 * longer reachable through {@link Process#descendants()}). {@link #killAll()} runs after each test class that starts
 * long-lived processes and, as a last resort, from a shutdown hook of the test JVM.
 */
public final class Processes {
  private static final Map<Process, Set<ProcessHandle>> TRACKED = new ConcurrentHashMap<>();

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(Processes::killAll, "booster-it-process-reaper"));
  }

  private Processes() {}

  /**
   * Remembers a process until it and its remembered descendants have terminated.
   *
   * @param process a started process
   * @return the same process
   */
  public static Process track(Process process) {
    TRACKED.put(process, ConcurrentHashMap.newKeySet());
    return process;
  }

  /**
   * Records the current descendants of a tracked process.
   *
   * @param process the process
   * @return every descendant recorded so far (alive or not)
   */
  public static List<ProcessHandle> rememberDescendants(Process process) {
    Set<ProcessHandle> known = TRACKED.computeIfAbsent(process, p -> ConcurrentHashMap.newKeySet());
    process.descendants().forEach(known::add);
    return List.copyOf(known);
  }

  /**
   * Forcibly kills a process and every descendant it is known to have.
   *
   * @param process the process
   */
  public static void killTree(Process process) {
    List<ProcessHandle> descendants = rememberDescendants(process);
    descendants.forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
    try {
      process.waitFor(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    awaitTermination(descendants, Duration.ofSeconds(10));
    TRACKED.remove(process);
  }

  /**
   * Forgets a process that terminated together with all its known descendants.
   *
   * @param process the process
   */
  public static void release(Process process) {
    List<ProcessHandle> descendants = rememberDescendants(process);
    if (!process.isAlive() && descendants.stream().noneMatch(ProcessHandle::isAlive)) {
      TRACKED.remove(process);
    }
  }

  /** Kills every tracked process that is still alive, or whose descendants are. */
  public static void killAll() {
    for (Process process : List.copyOf(TRACKED.keySet())) {
      killTree(process);
    }
  }

  /**
   * Waits until every handle has terminated.
   *
   * @param handles the processes
   * @param timeout how long to wait for all of them
   * @return the handles still alive after the timeout
   */
  public static List<ProcessHandle> awaitTermination(List<ProcessHandle> handles, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    for (ProcessHandle handle : handles) {
      long remaining = Math.max(0, deadline - System.nanoTime());
      handle.onExit().completeOnTimeout(handle, remaining, TimeUnit.NANOSECONDS).join();
    }
    return handles.stream().filter(ProcessHandle::isAlive).toList();
  }
}
