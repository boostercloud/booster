package com.boostercloud.framework.types.sensor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Defaults of {@code health-indicator-configuration.ts}. Each call returns new mutable objects, so configurations
 * never share state.
 */
public final class SensorDefaults {
  private SensorDefaults() {}

  /**
   * {@code DEFAULT_HEALTH_CONFIGURATION_BOOSTER}: {@code { enabled: false, details: true, showChildren: true }}.
   *
   * @return a new default configuration
   */
  public static HealthIndicatorConfigurationBase defaultHealthConfigurationBooster() {
    return new HealthIndicatorConfigurationBase(false, true, true);
  }

  /**
   * {@code DEFAULT_SENSOR_HEALTH_BOOSTER_CONFIGURATIONS}: one independent default per built-in indicator id.
   *
   * @return a new, mutable, insertion-ordered map
   */
  public static Map<String, HealthIndicatorConfigurationBase> defaultSensorHealthBoosterConfigurations() {
    Map<String, HealthIndicatorConfigurationBase> defaults = new LinkedHashMap<>();
    for (BoosterHealthIndicatorsIds id : BoosterHealthIndicatorsIds.values()) {
      defaults.put(id.id(), defaultHealthConfigurationBooster());
    }
    return defaults;
  }
}
