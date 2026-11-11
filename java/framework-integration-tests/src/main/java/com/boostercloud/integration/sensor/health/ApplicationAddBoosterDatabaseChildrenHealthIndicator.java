package com.boostercloud.integration.sensor.health;

import com.boostercloud.framework.core.decorators.HealthSensor;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.sensor.HealthIndicatorInterface;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorResult;
import com.boostercloud.framework.types.sensor.HealthStatus;

/**
 * An application indicator below the built-in {@code booster/database} ({@code BOOSTER_HEALTH_INDICATORS_IDS.DATABASE + '/myApplication'}) ({@code src/sensor/health/application-add-booster-database-children-health-indicator.ts}): always {@code UNKNOWN}, without details.
 */
@HealthSensor(id = "booster/database/myApplication", name = "Indicator added to the Booster Database indicator through My Application", enabled = true, details = true, showChildren = true)
public class ApplicationAddBoosterDatabaseChildrenHealthIndicator implements HealthIndicatorInterface {

  @Override
  public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata healthIndicatorMetadata) {
    return HealthIndicatorResult.of(HealthStatus.UNKNOWN);
  }
}
