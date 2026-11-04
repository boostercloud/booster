package io.boostercloud.booster.core;

import java.util.Map;
import java.util.UUID;

public record RequestContext(String requestId, UserContext user, Map<String, String> metadata) {
  public RequestContext {
    if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
    user = user == null ? UserContext.anonymous() : user;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
  public static RequestContext anonymous() { return new RequestContext(null, null, Map.of()); }
}
