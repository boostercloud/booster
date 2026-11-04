package io.boostercloud.booster.core;

import java.util.Set;

@FunctionalInterface
public interface AccessPolicy {
  boolean permits(UserContext user);
  static AccessPolicy deny() { return user -> false; }
  static AccessPolicy publicAccess() { return user -> true; }
  static AccessPolicy authenticated() { return UserContext::authenticated; }
  static AccessPolicy roles(String... roles) {
    var allowed = Set.of(roles);
    return user -> user.authenticated() && user.roles().stream().anyMatch(allowed::contains);
  }
}
