package com.boostercloud.framework.core.sensor.health;

import com.boostercloud.framework.types.BoosterConfig;

/**
 * Port of {@code sensor/health/booster-health-service.ts}: the entry point of the health endpoint (request →
 * envelope → authentication/authorization → selected indicators → resolved tree → aggregated status → provider
 * response). Never throws to the provider: failures go through {@code provider.api().requestFailed}.
 *
 * <p>Skeleton: implemented by stage <b>core-2b</b> (spec 09 §3.8).
 */
public class BoosterHealthService {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public BoosterHealthService(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code boosterHealth(request)}. Implemented by stage core-2b.
   *
   * @param request the provider-specific request
   * @return the provider-specific response
   */
  public Object boosterHealth(Object request) {
    throw new UnsupportedOperationException("not implemented yet: BoosterHealthService#boosterHealth");
  }
}
