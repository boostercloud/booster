package io.boostercloud.booster.core;

import java.util.Map;
import java.util.Set;

public record UserContext(String subject, Set<String> roles, Map<String, Object> claims) {
  public UserContext {
    roles = roles == null ? Set.of() : Set.copyOf(roles);
    claims = claims == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(claims));
  }
  public static UserContext anonymous() { return new UserContext(null, Set.of(), Map.of()); }
  public boolean authenticated() { return subject != null && !subject.isBlank(); }
}
