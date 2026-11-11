package com.boostercloud.framework.core.integration.app.health;

import com.boostercloud.framework.core.decorators.HealthSensor;
import com.boostercloud.framework.core.integration.app.AppState;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.sensor.HealthIndicatorInterface;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorResult;
import com.boostercloud.framework.types.sensor.HealthStatus;
import java.util.Map;

/** The payment gateway, a child of the store; its details are hidden. */
@HealthSensor(id = "store/payments", name = "Payments", enabled = true, details = false)
public class PaymentsHealth implements HealthIndicatorInterface {
  @Override
  public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata metadata) {
    return new HealthIndicatorResult(AppState.paymentsUp ? HealthStatus.UP : HealthStatus.DOWN,
        Map.of("gateway", "memory"));
  }
}
