package io.boostercloud.booster.core;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
/** Typed error boundary for replacement or explicit suppression by application policy. */
public record FrameworkError(Stage stage, String operation, JsonNode input, RequestContext context, Throwable error) {
  public enum Stage { COMMAND, QUERY, EVENT_HANDLER, REDUCER, PROJECTION, SNAPSHOT, EVENT, SCHEDULE }
  public FrameworkError { Objects.requireNonNull(stage); Objects.requireNonNull(error); input = input == null ? null : input.deepCopy(); }
  @Override public JsonNode input() { return input == null ? null : input.deepCopy(); }
}
