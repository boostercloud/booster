package io.boostercloud.booster.health;

import io.boostercloud.booster.core.UserContext;
import java.util.Map;

/** The authenticated context supplied to health authorization and sensors; tokens are never retained. */
public record HealthRequest(String componentPath, UserContext user, Map<String, String> metadata) {
  public HealthRequest {
    componentPath = componentPath == null ? "" : componentPath;
    HealthConfiguration.validatePath(componentPath, true);
    user = user == null ? UserContext.anonymous() : user;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
