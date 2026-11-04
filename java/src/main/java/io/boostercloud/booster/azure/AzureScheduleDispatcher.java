package io.boostercloud.booster.azure;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.*;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import io.boostercloud.booster.scheduling.PersistentScheduler;

/** Timer trigger boundary: every replica/retry uses the same nominal occurrence identifier. */
public final class AzureScheduleDispatcher {
  private final Booster runtime;
  private final PersistentScheduler scheduler;
  public AzureScheduleDispatcher(Booster runtime) { this.runtime = Objects.requireNonNull(runtime); this.scheduler = null; }
  /** Preferred for registered calendars: shares occurrence receipts with the hosted scheduler. */
  public AzureScheduleDispatcher(PersistentScheduler scheduler) { this.scheduler = Objects.requireNonNull(scheduler); this.runtime = null; }
  public void dispatchScheduledTask(String name, Instant scheduledAt) {
    if (scheduler == null) throw new IllegalStateException("A PersistentScheduler is required for registered tasks");
    scheduler.dispatch(name, scheduledAt);
  }
  public JsonNode dispatch(String schedule, String command, JsonNode input, UserContext identity, Instant scheduledAt) {
    if (runtime == null) throw new IllegalStateException("Use dispatchScheduledTask for a registered calendar");
    if (schedule == null || schedule.isBlank() || scheduledAt == null) throw new IllegalArgumentException("Schedule and nominal occurrence are required");
    String id = "azure-timer:" + CosmosEventStore.hash(schedule + "\n" + scheduledAt);
    return runtime.executeCommand(command, input, new RequestContext(id, identity,
        Map.of("transport", "azure-timer", "schedule", schedule, "scheduledAt", scheduledAt.toString())));
  }
}
