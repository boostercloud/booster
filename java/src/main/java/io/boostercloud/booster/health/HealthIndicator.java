package io.boostercloud.booster.health;

@FunctionalInterface
public interface HealthIndicator {
  HealthResult check(HealthConfiguration configuration, HealthRequest request) throws Exception;
}
