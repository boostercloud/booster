package io.boostercloud.booster.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import io.boostercloud.booster.core.Register;
import io.boostercloud.booster.core.UserContext;
import java.time.Instant;
import java.util.Objects;

/** Stable schedule identity, immutable input and explicit execution identity; callback code is trusted application code. */
public record ScheduledTask(String name, CalendarSchedule schedule, JsonNode input, UserContext user, Handler handler) {
  public ScheduledTask {
    if (name == null || name.isBlank() || name.length() > 256) throw new IllegalArgumentException("Invalid schedule name");
    Objects.requireNonNull(schedule); Objects.requireNonNull(user); Objects.requireNonNull(handler);
    input = input == null ? NullNode.instance : input.deepCopy();
  }
  @Override public JsonNode input() { return input.deepCopy(); }
  @FunctionalInterface public interface Handler { void handle(Occurrence occurrence, Register register) throws Exception; }
  public record Occurrence(String schedule, String generation, Instant scheduledAt, JsonNode input) {
    public Occurrence { Objects.requireNonNull(schedule); Objects.requireNonNull(generation); Objects.requireNonNull(scheduledAt); input = input.deepCopy(); }
    public Occurrence(String schedule, Instant scheduledAt, JsonNode input) { this(schedule, "1", scheduledAt, input); }
    @Override public JsonNode input() { return input.deepCopy(); }
  }
}
