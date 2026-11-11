package com.boostercloud.integration.sensor.health;

import com.boostercloud.framework.core.decorators.HealthSensor;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.sensor.HealthIndicatorInterface;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorResult;
import com.boostercloud.framework.types.sensor.HealthStatus;

/**
 * A second application indicator below the built-in {@code booster/database} ({@code BOOSTER_HEALTH_INDICATORS_IDS.DATABASE + '/myApplication2'}) ({@code src/sensor/health/application-add2-booster-database-children-health-indicator.ts}): always {@code UNKNOWN}, without details.
 */
@HealthSensor(id = "booster/database/myApplication2", name = "A second indicator added to the Booster Database indicator through My Application", enabled = true, details = true, showChildren = true)
public class ApplicationAdd2BoosterDatabaseChildrenHealthIndicator implements HealthIndicatorInterface {

  @Override
  public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata healthIndicatorMetadata) {
    return HealthIndicatorResult.of(HealthStatus.UNKNOWN);
  }
}
