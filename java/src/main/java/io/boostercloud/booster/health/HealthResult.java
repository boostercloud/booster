package io.boostercloud.booster.health;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.Json;
import java.util.Map;
import java.util.Objects;

public record HealthResult(HealthStatus status, JsonNode details) {
  public HealthResult {
    Objects.requireNonNull(status, "status");
    details = details == null ? null : details.deepCopy();
  }
  public HealthResult(HealthStatus status) { this(status, (JsonNode) null); }
  public static HealthResult of(HealthStatus status, Map<String, ?> details) {
    return new HealthResult(status, Json.MAPPER.valueToTree(details));
  }
  @Override public JsonNode details() { return details == null ? null : details.deepCopy(); }
}
