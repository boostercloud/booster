package com.boostercloud.framework.provider.local.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.boostercloud.framework.provider.local.library.ScheduledAdapter.LocalScheduleCommandEnvelope;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.ScheduleInterface;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code scheduler.ts} behaviour of {@link Scheduler}, driven by a manual clock. */
class SchedulerTest {

  static final class CheckCarts {}

  static final class Hourly {}

  static final class Empty {}

  private BoosterConfig config;
  private Logger logger;
  private FakeUserApp app;
  private ManualClock clock;
  private Scheduler scheduler;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    logger = mock(Logger.class);
    config.logger = logger;
    app = new FakeUserApp(config);
    clock = new ManualClock(ZonedDateTime.of(2024, 1, 31, 10, 3, 30, 0, ZoneId.of("UTC")));
  }

  @AfterEach
  void tearDown() {
    if (scheduler != null) {
      scheduler.close();
    }
  }

  private static ScheduledCommandMetadata schedule(Class<?> type, String minute, String hour) {
    return new ScheduledCommandMetadata(type, new ScheduleInterface(minute, hour, null, null, null, null));
  }

  @Test
  void createsTheFiveFieldExpressionWithStarsForMissingFieldsAndWithoutTheYear() {
    assertThat(Scheduler.createCronExpression(new ScheduledCommandMetadata(CheckCarts.class,
        new ScheduleInterface("0/5", null, null, null, null, "2020")))).isEqualTo("0/5 * * * *");
    assertThat(Scheduler.createCronExpression(new ScheduledCommandMetadata(CheckCarts.class,
        new ScheduleInterface("1", "2", "3", "4", "5", null)))).isEqualTo("1 2 3 4 5");
    assertThat(Scheduler.createCronExpression(new ScheduledCommandMetadata(CheckCarts.class,
        new ScheduleInterface("", " ", null, null, "MON", null)))).isEqualTo("* * * * MON");
  }

  @Test
  void buildsTheInfoOfACommand() {
    ScheduledCommandMetadata metadata = schedule(CheckCarts.class, "0", null);
    config.scheduledCommandHandlers.put("CheckCarts", metadata);
    assertThat(Scheduler.buildScheduledCommandInfo(config, "CheckCarts"))
        .isEqualTo(new Scheduler.ScheduledCommandInfo("CheckCarts", metadata));
  }

  @Test
  void schedulesOneJobPerCommandWithASchedule() {
    config.scheduledCommandHandlers.put("CheckCarts", schedule(CheckCarts.class, "0/5", null));
    config.scheduledCommandHandlers.put("Hourly", schedule(Hourly.class, "0", null));
    config.scheduledCommandHandlers.put("Empty", new ScheduledCommandMetadata(Empty.class,
        new ScheduleInterface(null, null, null, null, null, "2030")));
    config.scheduledCommandHandlers.put("NoSchedule", new ScheduledCommandMetadata(Empty.class, null));

    scheduler = Scheduler.configureScheduler(config, () -> app, clock);

    assertThat(scheduler.jobs()).extracting(Scheduler.ScheduledJob::name).containsExactly("CheckCarts", "Hourly");
    assertThat(scheduler.jobs()).extracting(job -> job.cron().expression()).containsExactly("0/5 * * * *", "0 * * * *");
  }

  @Test
  void triggersTheCommandAtEveryMatchingMinute() {
    config.scheduledCommandHandlers.put("CheckCarts", schedule(CheckCarts.class, "0/5", null));
    scheduler = Scheduler.configureScheduler(config, () -> app, clock);
    await().until(() -> clock.sleepers() == 1);
    assertThat(app.scheduledTriggers).isEmpty();

    clock.setTo(ZonedDateTime.of(2024, 1, 31, 10, 4, 59, 0, ZoneId.of("UTC")));
    clock.advance(Duration.ZERO);
    assertThat(app.scheduledTriggers).isEmpty();

    clock.setTo(ZonedDateTime.of(2024, 1, 31, 10, 5, 0, 0, ZoneId.of("UTC")));
    await().until(() -> app.scheduledTriggers.size() == 1);
    assertThat(app.scheduledTriggers.get(0)).isEqualTo(new LocalScheduleCommandEnvelope("CheckCarts"));

    clock.setTo(ZonedDateTime.of(2024, 1, 31, 10, 9, 0, 0, ZoneId.of("UTC")));
    await().until(() -> clock.sleepers() == 1);
    assertThat(app.scheduledTriggers).hasSize(1);

    clock.setTo(ZonedDateTime.of(2024, 1, 31, 10, 10, 0, 0, ZoneId.of("UTC")));
    await().until(() -> app.scheduledTriggers.size() == 2);
  }

  @Test
  void missedRunsAreRunLateOneAfterTheOther() {
    config.scheduledCommandHandlers.put("CheckCarts", schedule(CheckCarts.class, "*", null));
    scheduler = Scheduler.configureScheduler(config, () -> app, clock);
    await().until(() -> clock.sleepers() == 1);

    clock.advance(Duration.ofMinutes(3));

    await().until(() -> app.scheduledTriggers.size() == 3);
    assertThat(scheduler.triggerCount()).isEqualTo(3);
  }

  @Test
  void runsDoNotWaitForPreviousOnes() throws InterruptedException {
    CountDownLatch release = new CountDownLatch(1);
    app.onScheduled(trigger -> {
      try {
        release.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    config.scheduledCommandHandlers.put("CheckCarts", schedule(CheckCarts.class, "*", null));
    scheduler = Scheduler.configureScheduler(config, () -> app, clock);
    await().until(() -> clock.sleepers() == 1);

    clock.advance(Duration.ofMinutes(1));
    await().until(() -> app.scheduledTriggers.size() == 1);
    clock.advance(Duration.ofMinutes(1));
    await().until(() -> app.scheduledTriggers.size() == 2);
    release.countDown();
    assertThat(scheduler.close(Duration.ofSeconds(5))).isTrue();
  }

  @Test
  void failingRunsAreLoggedAndTheJobGoesOn() {
    app.onScheduled(trigger -> {
      throw new IllegalStateException("command failed");
    });
    config.scheduledCommandHandlers.put("CheckCarts", schedule(CheckCarts.class, "*", null));
    scheduler = Scheduler.configureScheduler(config, () -> app, clock);
    await().until(() -> clock.sleepers() == 1);

    clock.advance(Duration.ofMinutes(1));
    await().untilAsserted(() -> verify(logger).error(eq("[Booster]|scheduler#triggerScheduledCommand: "),
        eq("The scheduled command CheckCarts failed"), any(IllegalStateException.class)));
    clock.advance(Duration.ofMinutes(1));
    await().until(() -> app.scheduledTriggers.size() == 2);
  }

  @Test
  void invalidSchedulesAreLoggedAndNotScheduled() {
    config.scheduledCommandHandlers.put("CheckCarts", schedule(CheckCarts.class, "61", null));
    config.scheduledCommandHandlers.put("Never", new ScheduledCommandMetadata(Hourly.class,
        new ScheduleInterface("0", "0", "30", "2", null, null)));

    scheduler = Scheduler.configureScheduler(config, () -> app, clock);

    assertThat(scheduler.jobs()).isEmpty();
    verify(logger).warn(eq("[Booster]|scheduler#configureScheduler: "),
        contains("The scheduled command CheckCarts has an invalid schedule '61 * * * *'"));
    verify(logger).warn(anyString(), contains("never matches"));
  }

  @Test
  void closeStopsScheduling() {
    config.scheduledCommandHandlers.put("CheckCarts", schedule(CheckCarts.class, "*", null));
    scheduler = Scheduler.configureScheduler(config, () -> app, clock);
    await().until(() -> clock.sleepers() == 1);

    assertThat(scheduler.close(Duration.ofSeconds(5))).isTrue();
    assertThat(scheduler.isStopped()).isTrue();
    clock.advance(Duration.ofMinutes(10));

    assertThat(app.scheduledTriggers).isEmpty();
  }

  @Test
  void theTypeScriptSignatureUsesTheSystemClock() {
    config.scheduledCommandHandlers.put("Hourly", schedule(Hourly.class, "0", null));
    scheduler = Scheduler.configureScheduler(config, app);
    assertThat(scheduler.jobs()).hasSize(1);
  }
}
