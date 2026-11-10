package com.boostercloud.integration.sensor.health;

import com.boostercloud.framework.core.decorators.HealthSensor;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.sensor.HealthIndicatorInterface;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorResult;
import com.boostercloud.framework.types.sensor.HealthStatus;

/**
 * A child of {@link ApplicationHealthIndicator} (by id prefix) ({@code src/sensor/health/application-child-health-indicator.ts}): always {@code OUT_OF_SERVICE}, without details.
 */
@HealthSensor(id = "myApplication/child", name = "My Application child", enabled = true, details = true, showChildren = true)
public class ApplicationChildHealthIndicator implements HealthIndicatorInterface {

  @Override
  public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata healthIndicatorMetadata) {
    return HealthIndicatorResult.of(HealthStatus.OUT_OF_SERVICE);
  }
}
