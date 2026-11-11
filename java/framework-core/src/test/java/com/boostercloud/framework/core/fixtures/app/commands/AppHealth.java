package com.boostercloud.framework.core.fixtures.app.commands;

import com.boostercloud.framework.core.decorators.HealthSensor;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.sensor.HealthIndicatorInterface;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorResult;
import com.boostercloud.framework.types.sensor.HealthStatus;

/** Sorted before {@code config}: proves that configurators run before the annotations are applied. */
@HealthSensor(id = "app", name = "App", enabled = true, details = true)
public class AppHealth implements HealthIndicatorInterface {
  @Override
  public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata metadata) {
    return HealthIndicatorResult.of(HealthStatus.UP);
  }
}
