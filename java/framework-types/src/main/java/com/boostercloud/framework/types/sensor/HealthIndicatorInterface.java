package com.boostercloud.framework.types.sensor;

import com.boostercloud.framework.types.BoosterConfig;

/**
 * A health indicator. Port of {@code HealthIndicatorInterface}: core instantiates the registered class with its
 * no-argument constructor and calls {@link #health(BoosterConfig, HealthIndicatorMetadata)}.
 */
public interface HealthIndicatorInterface {
  /**
   * @param config the application configuration
   * @param healthIndicatorMetadata the metadata of this indicator
   * @return the result
   */
  HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata healthIndicatorMetadata);
}
