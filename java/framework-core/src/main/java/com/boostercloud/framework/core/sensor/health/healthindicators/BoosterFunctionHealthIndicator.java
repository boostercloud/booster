package com.boostercloud.framework.core.sensor.health.healthindicators;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.sensor.HealthIndicatorInterface;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorResult;
import com.boostercloud.framework.types.sensor.HealthStatus;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code sensor/health/health-indicators/booster-function-health-indicator.ts}: the built-in indicator
 * {@code booster/function} (the GraphQL function).
 *
 * <p>The status comes from {@code isGraphQLFunctionUp}; the details are the {@link OsInfo#osInfo() OS information}
 * ({@code cpus}, {@code memory}) plus {@code graphQL_url}. Any exception gives {@code DOWN} with the error as details.
 */
public class BoosterFunctionHealthIndicator implements HealthIndicatorInterface {

  /** Creates the indicator (the health service instantiates it with no arguments). */
  public BoosterFunctionHealthIndicator() {}

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
      HealthStatus status = config.provider().sensor().isGraphQLFunctionUp(config) ? HealthStatus.UP : HealthStatus.DOWN;
      Map<String, Object> details = null;
      if (healthIndicatorMetadata.healthIndicatorConfiguration().details) {
        String graphQLUrl = config.provider().sensor().graphQLFunctionUrl(config);
        details = new LinkedHashMap<>(BoosterJson.toRawMap(OsInfo.osInfo()));
        details.put("graphQL_url", graphQLUrl);
      }
      return new HealthIndicatorResult(status, details);
    } catch (Exception e) {
      return new HealthIndicatorResult(HealthStatus.DOWN, BoosterJson.toRawMap(e));
    }
  }
}
