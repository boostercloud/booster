package com.boostercloud.framework.core.integration.app.health;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.HealthSensor;
import com.boostercloud.framework.core.integration.app.readmodels.ProductReadModel;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.sensor.HealthIndicatorInterface;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorResult;
import com.boostercloud.framework.types.sensor.HealthStatus;
import java.util.Map;

/** The health of the store: up, with the number of products as details. */
@HealthSensor(id = "store", name = "Store", enabled = true, details = true)
public class StoreHealth implements HealthIndicatorInterface {
  @Override
  public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata metadata) {
    return new HealthIndicatorResult(HealthStatus.UP,
        Map.of("products", Booster.readModel(ProductReadModel.class).search().size()));
  }
}
