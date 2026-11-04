package io.boostercloud.booster.health;

import io.boostercloud.booster.core.AccessPolicy;
import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.core.RequestContext;
import java.util.Objects;

@FunctionalInterface
public interface HealthAuthorizer {
  boolean permits(HealthRequest request);
  static HealthAuthorizer policy(AccessPolicy policy) {
    Objects.requireNonNull(policy);
    return request -> policy.permits(new RequestContext(null, request.user(), request.metadata()),
        Json.MAPPER.createObjectNode().put("componentPath", request.componentPath()));
  }
}
