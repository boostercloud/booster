package com.boostercloud.integration.sensor.health;

import com.boostercloud.framework.core.decorators.HealthSensor;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.sensor.HealthIndicatorInterface;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorResult;
import com.boostercloud.framework.types.sensor.HealthStatus;

/**
 * The application's own health indicator ({@code src/sensor/health/application-health-indicator.ts}): always {@code UP}, without details.
 */
@HealthSensor(id = "myApplication", name = "my-application", enabled = true, details = true, showChildren = true)
public class ApplicationHealthIndicator implements HealthIndicatorInterface {

  @Override
  public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata healthIndicatorMetadata) {
    return HealthIndicatorResult.of(HealthStatus.UP);
  }
}
