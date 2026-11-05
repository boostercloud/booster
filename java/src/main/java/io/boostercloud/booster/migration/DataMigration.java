package io.boostercloud.booster.migration;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.Json;
import java.util.Objects;

/** Stable name, execution order and parameters form a durable migration definition. */
public record DataMigration(String name, int order, JsonNode parameters, Handler handler) {
  public DataMigration {
    if (name == null || name.isBlank() || name.length() > 200)
      throw new IllegalArgumentException("Migration names must contain 1 to 200 characters");
    parameters = parameters == null ? Json.MAPPER.createObjectNode() : parameters.deepCopy();
    Objects.requireNonNull(handler, "handler");
  }
  public DataMigration(String name, int order, Handler handler) { this(name, order, null, handler); }
  @Override public JsonNode parameters() { return parameters.deepCopy(); }
  @FunctionalInterface public interface Handler { void start(MigrationContext context) throws Exception; }
}
