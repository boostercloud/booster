package com.boostercloud.framework.types.sensor;

import com.boostercloud.framework.types.concepts.HealthRoleAccess;
import java.util.Map;

/**
 * Health sensor settings. Port of {@code SensorBoosterHealthConfiguration}.
 */
public class SensorBoosterHealthConfiguration {
  /** Who may read the health endpoint (default {@code { authorize: 'all' }}). */
  public HealthRoleAccess globalAuthorizer = HealthRoleAccess.all();

  /**
   * Settings of the built-in indicators by id ({@link BoosterHealthIndicatorsIds#id()}), in the TypeScript order. A
   * fresh copy of the defaults per configuration (TypeScript shares the default object; see the README).
   */
  public final Map<String, HealthIndicatorConfigurationBase> booster = SensorDefaults.defaultSensorHealthBoosterConfigurations();

  /**
   * @param id a built-in indicator
   * @return its settings
   */
  public HealthIndicatorConfigurationBase get(BoosterHealthIndicatorsIds id) {
    return booster.get(id.id());
  }
}
