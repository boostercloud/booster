package com.boostercloud.framework.provider.local.infrastructure;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.local.library.ScheduledAdapter.LocalScheduleCommandEnvelope;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.ScheduleInterface;
import com.boostercloud.framework.types.UserApp;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Runs the scheduled commands of the application. Port of {@code scheduler.ts}, which registers one
 * {@code node-schedule} job per scheduled command.
 *
 * <p>Every entry of {@code config.scheduledCommandHandlers} with a non-empty {@code scheduledOn} becomes a job named
 * after the command, with the 5-field cron expression {@code minute hour day month weekDay} (missing fields are
 * {@code *}, {@code year} is ignored) evaluated by {@link CronExpression} in the clock's zone. At every matching
 * minute (second 0) the job calls {@code userApp.boosterTriggerScheduledCommands({ typeName })} on a new virtual
 * thread, without waiting for previous runs (runs can overlap, as in TypeScript). A schedule with no field set is
 * never run, as the documentation states and the Azure provider does (TypeScript's local provider runs it every
 * minute); an invalid expression is logged and not scheduled (TypeScript schedules nothing, silently); a failing run is
 * logged (TypeScript leaves an unhandled rejection).
 *
 * <p>Runs that were missed because the process could not keep up are run late, one after the other, like
 * {@code node-schedule} does. {@link #close()} stops scheduling and waits for the running triggers.
 */
public final class Scheduler implements AutoCloseable {

  /** How long {@link #close()} waits for running triggers by default. */
  public static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(10);

  /**
   * {@code ScheduledCommandInfo}: a scheduled command and its metadata.
   *
   * @param name the command name (the job name)
   * @param metadata its metadata
   */
  public record ScheduledCommandInfo(String name, ScheduledCommandMetadata metadata) {}

  /**
   * A registered job.
   *
   * @param name the scheduled command name
   * @param cron its cron expression
   */
  public record ScheduledJob(String name, CronExpression cron) {}

  private final BoosterConfig config;
  private final Supplier<UserApp> userApp;
  private final SchedulerClock clock;
  private final Logger logger;
  private final List<ScheduledJob> jobs = new ArrayList<>();
  private final List<Thread> jobThreads = new ArrayList<>();
  private final Set<Thread> runningTriggers = ConcurrentHashMap.newKeySet();
  private final AtomicLong triggerCount = new AtomicLong();
  private volatile boolean stopped;

  private Scheduler(BoosterConfig config, Supplier<UserApp> userApp, SchedulerClock clock) {
    this.config = config;
    this.userApp = userApp;
    this.clock = clock;
    this.logger = Loggers.getLogger(config, "scheduler#configureScheduler");
  }

  /**
   * {@code configureScheduler(config, userProject)} with the system clock.
   *
   * @param config the configuration
   * @param userProject the application
   * @return the running scheduler
   */
  public static Scheduler configureScheduler(BoosterConfig config, UserApp userProject) {
    Objects.requireNonNull(userProject, "userProject");
    return configureScheduler(config, () -> userProject, SchedulerClock.system());
  }

  /**
   * {@code configureScheduler(config, userProject)}.
   *
   * @param config the configuration
   * @param userApp resolves the application when a command runs
   * @param clock the time source
   * @return the running scheduler
   */
  public static Scheduler configureScheduler(BoosterConfig config, Supplier<UserApp> userApp, SchedulerClock clock) {
    Scheduler scheduler = new Scheduler(Objects.requireNonNull(config, "config"),
        Objects.requireNonNull(userApp, "userApp"), Objects.requireNonNull(clock, "clock"));
    config.scheduledCommandHandlers.keySet().stream()
        .map(name -> buildScheduledCommandInfo(config, name))
        .filter(info -> isScheduled(info.metadata()))
        .forEach(scheduler::scheduleJob);
    return scheduler;
  }

  /**
   * {@code buildScheduledCommandInfo(config, scheduledCommandName)}.
   *
   * @param config the configuration
   * @param scheduledCommandName the command name
   * @return the command and its metadata
   */
  public static ScheduledCommandInfo buildScheduledCommandInfo(BoosterConfig config, String scheduledCommandName) {
    return new ScheduledCommandInfo(scheduledCommandName, config.scheduledCommandHandlers.get(scheduledCommandName));
  }

  /**
   * {@code createCronExpression(scheduledCommandMetadata)}: {@code minute hour day month weekDay}, each missing (or
   * empty) field as {@code *}; {@code year} is ignored.
   *
   * @param scheduledCommandMetadata the metadata
   * @return the expression
   */
  public static String createCronExpression(ScheduledCommandMetadata scheduledCommandMetadata) {
    return normalize(scheduledCommandMetadata.scheduledOn()).toFiveFieldCron();
  }

  /**
   * @param metadata a scheduled command's metadata
   * @return whether it has a schedule with at least one field (other than {@code year}) set
   */
  public static boolean isScheduled(ScheduledCommandMetadata metadata) {
    if (metadata == null || metadata.scheduledOn() == null) {
      return false;
    }
    ScheduleInterface schedule = normalize(metadata.scheduledOn());
    return !(schedule.minute() == null && schedule.hour() == null && schedule.day() == null
        && schedule.month() == null && schedule.weekDay() == null);
  }

  private static ScheduleInterface normalize(ScheduleInterface schedule) {
    return new ScheduleInterface(blankToNull(schedule.minute()), blankToNull(schedule.hour()),
        blankToNull(schedule.day()), blankToNull(schedule.month()), blankToNull(schedule.weekDay()),
        blankToNull(schedule.year()));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private void scheduleJob(ScheduledCommandInfo info) {
    String expression = createCronExpression(info.metadata());
    CronExpression cron;
    try {
      cron = CronExpression.parse(expression);
    } catch (IllegalArgumentException e) {
      logger.warn("The scheduled command " + info.name() + " has an invalid schedule '" + expression + "' ("
          + e.getMessage() + "); it will not run");
      return;
    }
    if (cron.next(ZonedDateTime.ofInstant(clock.now(), clock.zone())).isEmpty()) {
      logger.warn("The schedule '" + expression + "' of the scheduled command " + info.name()
          + " never matches; it will not run");
      return;
    }
    ScheduledJob job = new ScheduledJob(info.name(), cron);
    jobs.add(job);
    Thread thread = Thread.ofVirtual().name("booster-scheduler-" + info.name()).unstarted(() -> runJob(job));
    jobThreads.add(thread);
    logger.debug("Scheduled command " + info.name() + " scheduled with cron '" + expression + "'");
    thread.start();
  }

  private void runJob(ScheduledJob job) {
    ZonedDateTime last = ZonedDateTime.ofInstant(clock.now(), clock.zone());
    while (!stopped) {
      Optional<ZonedDateTime> next = job.cron().next(last);
      if (next.isEmpty()) {
        return;
      }
      Instant deadline = next.get().toInstant();
      try {
        while (!stopped && clock.now().isBefore(deadline)) {
          clock.sleepUntil(deadline);
        }
      } catch (InterruptedException e) {
        return;
      }
      if (stopped) {
        return;
      }
      trigger(job.name());
      last = next.get();
    }
  }

  private void trigger(String name) {
    Thread run = Thread.ofVirtual().name("booster-scheduled-" + name).unstarted(() -> {
      try {
        userApp.get().boosterTriggerScheduledCommands(new LocalScheduleCommandEnvelope(name));
      } catch (RuntimeException | Error e) {
        if (e instanceof VirtualMachineError fatal) {
          throw fatal;
        }
        Loggers.getLogger(config, "scheduler#triggerScheduledCommand")
            .error("The scheduled command " + name + " failed", e);
      } finally {
        runningTriggers.remove(Thread.currentThread());
      }
    });
    runningTriggers.add(run);
    triggerCount.incrementAndGet();
    run.start();
  }

  /**
   * @return the registered jobs, in configuration order
   */
  public List<ScheduledJob> jobs() {
    return List.copyOf(jobs);
  }

  /**
   * @return how many runs were triggered so far
   */
  public long triggerCount() {
    return triggerCount.get();
  }

  /**
   * @return whether {@link #close()} was called
   */
  public boolean isStopped() {
    return stopped;
  }

  /** Stops scheduling and waits up to {@link #DEFAULT_CLOSE_TIMEOUT} for the running triggers. */
  @Override
  public void close() {
    close(DEFAULT_CLOSE_TIMEOUT);
  }

  /**
   * Stops scheduling and waits for the running triggers.
   *
   * @param timeout the maximum wait
   * @return whether every running trigger finished in time
   */
  public boolean close(Duration timeout) {
    stopped = true;
    jobThreads.forEach(Thread::interrupt);
    long deadline = System.nanoTime() + timeout.toNanos();
    try {
      for (Thread thread : jobThreads) {
        thread.join(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())));
      }
      for (Thread thread : List.copyOf(runningTriggers)) {
        if (!thread.join(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())))) {
          return false;
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
    return runningTriggers.isEmpty();
  }
}
