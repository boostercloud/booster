package com.boostercloud.framework.core.sensor.health.healthindicators;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.provider.ProviderSensorLibrary;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthStatus;

/**
 * Port of {@code sensor/health/health-indicators/booster-database-read-models-health-indicator.ts}: the built-in
 * indicator {@code booster/database/readmodels} (the read models database).
 *
 * <p>The status comes from {@code areDatabaseReadModelsUp}; the details are
 * {@code databaseReadModelsHealthDetails(config)} verbatim (provider-defined: the Azure provider returns an array).
 * Any exception gives {@code DOWN} with the error as details. It returns a {@link RawHealthIndicatorResult} because
 * provider details are not necessarily an object.
 */
public class BoosterDatabaseReadModelsHealthIndicator {

  /** Creates the indicator (the health service instantiates it with no arguments). */
  public BoosterDatabaseReadModelsHealthIndicator() {}

  /**
   * Computes the health of this component.
   *
   * @param config the configuration
   * @param healthIndicatorMetadata the indicator metadata
   * @return the result
   */
  public RawHealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata healthIndicatorMetadata) {
    try {
      ProviderSensorLibrary sensor = config.provider().sensor();
      HealthStatus status = sensor.areDatabaseReadModelsUp(config) ? HealthStatus.UP : HealthStatus.DOWN;
      Object details = null;
      if (healthIndicatorMetadata.healthIndicatorConfiguration().details) {
        details = sensor.databaseReadModelsHealthDetails(config);
      }
      return new RawHealthIndicatorResult(status, details);
    } catch (Exception e) {
      return new RawHealthIndicatorResult(HealthStatus.DOWN, BoosterJson.toRawMap(e));
    }
  }
}
