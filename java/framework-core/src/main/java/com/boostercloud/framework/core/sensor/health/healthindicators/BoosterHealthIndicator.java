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
 * Port of {@code sensor/health/health-indicators/booster-health-indicator.ts}: the built-in indicator {@code booster}
 * (root of the Booster tree).
 *
 * <p>The status is {@code UP} when the GraphQL function, the events database and the read models database are all up
 * (the three sensor calls always run, in that order); the details are {@code { boosterVersion }}. Any exception gives
 * {@code DOWN} with the error as details.
 */
public class BoosterHealthIndicator implements HealthIndicatorInterface {

  /** Creates the indicator (the health service instantiates it with no arguments). */
  public BoosterHealthIndicator() {}

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
      HealthStatus status = isUp(config);
      Map<String, Object> details = null;
      if (healthIndicatorMetadata.healthIndicatorConfiguration().details) {
        details = new LinkedHashMap<>();
        details.put("boosterVersion", BoosterVersion.boosterVersion(config));
      }
      return new HealthIndicatorResult(status, details);
    } catch (Exception e) {
      return new HealthIndicatorResult(HealthStatus.DOWN, BoosterJson.toRawMap(e));
    }
  }

  private HealthStatus isUp(BoosterConfig config) {
    ProviderSensorLibrary sensor = config.provider().sensor();
    boolean graphqlUp = sensor.isGraphQLFunctionUp(config);
    boolean databaseEvents = sensor.isDatabaseEventUp(config);
    boolean databaseReadModels = sensor.areDatabaseReadModelsUp(config);
    return graphqlUp && databaseEvents && databaseReadModels ? HealthStatus.UP : HealthStatus.DOWN;
  }
}
