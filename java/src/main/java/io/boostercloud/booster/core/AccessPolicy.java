package io.boostercloud.booster.core;

import java.util.Set;
import java.util.Objects;
import java.util.function.Predicate;
import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface AccessPolicy {
  boolean permits(UserContext user);
  default boolean permits(RequestContext context, JsonNode input) { return permits(context.user()); }
  static AccessPolicy request(Predicate<RequestContext> predicate) {
    Objects.requireNonNull(predicate);
    return new AccessPolicy() {
      public boolean permits(UserContext user) { return false; }
      public boolean permits(RequestContext context, JsonNode input) { return predicate.test(context); }
    };
  }
  static AccessPolicy input(java.util.function.BiPredicate<RequestContext,JsonNode> predicate) {
    Objects.requireNonNull(predicate);
    return new AccessPolicy() {
      public boolean permits(UserContext user) { return false; }
      public boolean permits(RequestContext context, JsonNode input) { return predicate.test(context, input == null ? null : input.deepCopy()); }
    };
  }
  static AccessPolicy deny() { return user -> false; }
  static AccessPolicy publicAccess() { return user -> true; }
  static AccessPolicy authenticated() { return UserContext::authenticated; }
  static AccessPolicy roles(String... roles) {
    var allowed = Set.of(roles);
    return user -> user.authenticated() && user.roles().stream().anyMatch(allowed::contains);
  }
}
