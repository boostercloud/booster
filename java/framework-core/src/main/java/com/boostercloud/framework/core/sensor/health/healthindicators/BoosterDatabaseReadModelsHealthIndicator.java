package com.boostercloud.framework.core.sensor.health.healthindicators;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.sensor.HealthIndicatorInterface;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorResult;

/**
 * Port of {@code sensor/health/health-indicators/booster-database-read-models-health-indicator.ts}: the built-in indicator {@code booster/database/readmodels} (the read models database).
 *
 * <p>Skeleton: implemented by stage <b>core-2b</b> (spec 09 §3.9).
 */
public class BoosterDatabaseReadModelsHealthIndicator implements HealthIndicatorInterface {

  /** Creates the indicator (the health service instantiates it with no arguments). */
  public BoosterDatabaseReadModelsHealthIndicator() {}

  /**
   * Computes the health of this component. Implemented by stage core-2b.
   *
   * @param config the configuration
   * @param healthIndicatorMetadata the indicator metadata
   * @return the result
   */
  @Override
  public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata healthIndicatorMetadata) {
    throw new UnsupportedOperationException("not implemented yet: BoosterDatabaseReadModelsHealthIndicator#health");
  }
}
