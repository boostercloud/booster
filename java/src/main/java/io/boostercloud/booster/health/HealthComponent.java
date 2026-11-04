package io.boostercloud.booster.health;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthComponent(String name, String id, HealthStatus status, JsonNode details, List<HealthComponent> components) {
  public HealthComponent {
    details = details == null ? null : details.deepCopy();
    components = components == null || components.isEmpty() ? null : List.copyOf(components);
  }
  @Override public JsonNode details() { return details == null ? null : details.deepCopy(); }
}
