package io.boostercloud.booster.core;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import com.fasterxml.jackson.core.type.TypeReference;

public record UserContext(String subject, Set<String> roles, Map<String, Object> claims) {
  public UserContext {
    roles = roles == null ? Set.of() : Set.copyOf(roles);
    claims = claims == null ? Map.of() : immutableClaims(claims);
  }
  public static UserContext anonymous() { return new UserContext(null, Set.of(), Map.of()); }
  public boolean authenticated() { return subject != null && !subject.isBlank(); }

  private static Map<String, Object> immutableClaims(Map<String, Object> claims) {
    Map<String, Object> copy = Json.MAPPER.convertValue(claims, new TypeReference<Map<String, Object>>() {});
    copy.replaceAll((key, value) -> freeze(value));
    return Collections.unmodifiableMap(copy);
  }
  private static Object freeze(Object value) {
    if (value instanceof Map<?, ?> map) {
      var result = new LinkedHashMap<String, Object>();
      map.forEach((key, item) -> result.put((String) key, freeze(item)));
      return Collections.unmodifiableMap(result);
    }
    if (value instanceof List<?> list) {
      var result = new ArrayList<Object>();
      list.forEach(item -> result.add(freeze(item)));
      return Collections.unmodifiableList(result);
    }
    return value;
  }
}
