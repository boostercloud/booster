package com.boostercloud.framework.core.sensor.health.healthindicators;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.provider.ProviderSensorLibrary;
import com.boostercloud.framework.types.sensor.HealthIndicatorInterface;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorResult;
import com.boostercloud.framework.types.sensor.HealthStatus;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code sensor/health/health-indicators/booster-database-health-indicator.ts}: the built-in indicator
 * {@code booster/database} (the databases).
 *
 * <p>The status is {@code UP} when the events database and the read models database are both up (both sensor calls
 * always run); the details are {@code { urls: databaseUrls(config) }}. Any exception gives {@code DOWN} with the error
 * as details.
 */
public class BoosterDatabaseHealthIndicator implements HealthIndicatorInterface {

  /** Creates the indicator (the health service instantiates it with no arguments). */
  public BoosterDatabaseHealthIndicator() {}

  /**
   * Computes the health of this component.
   *
   * @param config the configuration
   * @param healthIndicatorMetadata the indicator metadata
   * @return the result
   */
  @Override
  public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata healthIndicatorMetadata) {
    try {
      ProviderSensorLibrary sensor = config.provider().sensor();
      boolean databaseEvents = sensor.isDatabaseEventUp(config);
      boolean databaseReadModels = sensor.areDatabaseReadModelsUp(config);
      HealthStatus status = databaseEvents && databaseReadModels ? HealthStatus.UP : HealthStatus.DOWN;
      Map<String, Object> details = null;
      if (healthIndicatorMetadata.healthIndicatorConfiguration().details) {
        details = new LinkedHashMap<>();
        details.put("urls", sensor.databaseUrls(config));
      }
      return new HealthIndicatorResult(status, details);
    } catch (Exception e) {
      return new HealthIndicatorResult(HealthStatus.DOWN, BoosterJson.toRawMap(e));
    }
  }
}
