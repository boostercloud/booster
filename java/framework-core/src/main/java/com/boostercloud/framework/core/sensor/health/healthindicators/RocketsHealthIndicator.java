package com.boostercloud.framework.core.sensor.health.healthindicators;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorsResult;

/**
 * Port of {@code sensor/health/health-indicators/rockets-health-indicator.ts}: the built-in indicator {@code rockets}
 * (and {@code rockets/<rocket>}), which returns the richer {@link HealthIndicatorsResult} (TypeScript returns
 * {@code HealthIndicatorsResult} too, so it does not implement {@code HealthIndicatorInterface}).
 *
 * <p>Skeleton: implemented by stage <b>core-2b</b> (spec 09 §3.9.3).
 */
public class RocketsHealthIndicator {

  /** Creates the indicator. */
  public RocketsHealthIndicator() {}

  /**
   * Computes the health of the rocket functions. Implemented by stage core-2b.
   *
   * @param config the configuration
   * @param healthIndicatorMetadata the indicator metadata
   * @return the result, with one component per rocket
   */
  public HealthIndicatorsResult health(BoosterConfig config, HealthIndicatorMetadata healthIndicatorMetadata) {
    throw new UnsupportedOperationException("not implemented yet: RocketsHealthIndicator#health");
  }
}
