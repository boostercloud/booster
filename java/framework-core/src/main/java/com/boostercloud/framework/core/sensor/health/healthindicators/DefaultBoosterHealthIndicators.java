package com.boostercloud.framework.core.sensor.health.healthindicators;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.sensor.BoosterHealthIndicatorsIds;
import com.boostercloud.framework.types.sensor.HealthIndicatorConfiguration;
import com.boostercloud.framework.types.sensor.HealthIndicatorConfigurationBase;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code sensor/health/health-indicators/default-booster-health-indicators.ts}
 * ({@code defaultBoosterHealthIndicators(config)}).
 */
public final class DefaultBoosterHealthIndicators {

  private DefaultBoosterHealthIndicators() {}

  /**
   * The six built-in indicators, in this order: {@code booster} (Booster), {@code booster/function} (Booster
   * Function), {@code booster/database} (Booster Database), {@code booster/database/events} (Booster Database Events),
   * {@code booster/database/readmodels} (Booster Database ReadModels), {@code rockets} (Rockets). {@code enabled},
   * {@code details} and {@code showChildren} are copied from {@code config.sensorConfiguration.health.booster} at call
   * time.
   *
   * @param config the configuration
   * @return a new mutable, insertion-ordered map from id to metadata
   */
  public static Map<String, HealthIndicatorMetadata> defaultBoosterHealthIndicators(BoosterConfig config) {
    Map<String, HealthIndicatorMetadata> indicators = new LinkedHashMap<>();
    add(indicators, config, BoosterHealthIndicatorsIds.ROOT, "Booster", BoosterHealthIndicator.class);
    add(indicators, config, BoosterHealthIndicatorsIds.FUNCTION, "Booster Function", BoosterFunctionHealthIndicator.class);
    add(indicators, config, BoosterHealthIndicatorsIds.DATABASE, "Booster Database", BoosterDatabaseHealthIndicator.class);
    add(indicators, config, BoosterHealthIndicatorsIds.DATABASE_EVENTS, "Booster Database Events",
        BoosterDatabaseEventsHealthIndicator.class);
    add(indicators, config, BoosterHealthIndicatorsIds.DATABASE_READ_MODELS, "Booster Database ReadModels",
        BoosterDatabaseReadModelsHealthIndicator.class);
    add(indicators, config, BoosterHealthIndicatorsIds.ROCKETS, "Rockets", RocketsHealthIndicator.class);
    return indicators;
  }

  private static void add(Map<String, HealthIndicatorMetadata> indicators, BoosterConfig config,
      BoosterHealthIndicatorsIds id, String name, Class<?> type) {
    HealthIndicatorConfigurationBase settings = config.sensorConfiguration.health.get(id);
    HealthIndicatorConfiguration configuration =
        new HealthIndicatorConfiguration(id.id(), name, settings.enabled, settings.details, settings.showChildren);
    indicators.put(id.id(), new HealthIndicatorMetadata(type, configuration));
  }
}
