package com.boostercloud.framework.core.sensor.health.healthindicators;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.sensor.BoosterHealthIndicatorsIds;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorsResult;
import com.boostercloud.framework.types.sensor.HealthStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code sensor/health/health-indicators/rockets-health-indicator.ts}: the built-in indicator {@code rockets}
 * (and {@code rockets/<rocket>}), which returns the richer {@link HealthIndicatorsResult} (TypeScript returns
 * {@code HealthIndicatorsResult} too, so it does not implement {@code HealthIndicatorInterface}).
 *
 * <p>Branches, in order (from {@code provider.sensor().areRocketFunctionsUp(config)}):
 *
 * <ol>
 *   <li>no rockets: {@code { name: "Rockets", id: "rockets", status: UNKNOWN, details: { reason: "No Rockets found" } }}
 *       (also when a specific rocket was requested);</li>
 *   <li>a rocket name in the metadata id ({@code rockets/<name>}, the second path segment): the status of that rocket
 *       ({@code UP}/{@code DOWN}) as {@code { name, id: "rockets/<name>", status }}, or
 *       {@code Rocket "<name>" not found} ({@link IllegalStateException}) when the provider does not know it;</li>
 *   <li>otherwise: {@code { name: "Rockets", id: "rockets", status, components }} with one component per rocket (map
 *       order); the status is {@code UP} when every rocket is up, {@code DOWN} when none is, else
 *       {@code PARTIALLY_UP}.</li>
 * </ol>
 *
 * Errors are not caught here: they fail the whole health request.
 */
public class RocketsHealthIndicator {

  private static final String ROCKETS_ID = BoosterHealthIndicatorsIds.ROCKETS.id();

  /** Creates the indicator. */
  public RocketsHealthIndicator() {}

  /**
   * Computes the health of the rocket functions.
   *
   * @param config the configuration
   * @param healthIndicatorMetadata the indicator metadata
   * @return the result, with one component per rocket
   */
  public HealthIndicatorsResult health(BoosterConfig config, HealthIndicatorMetadata healthIndicatorMetadata) {
    Map<String, Boolean> results = config.provider().sensor().areRocketFunctionsUp(config);
    if (results == null || results.isEmpty()) {
      return new HealthIndicatorsResult(HealthStatus.UNKNOWN, Map.of("reason", "No Rockets found"), "Rockets", ROCKETS_ID,
          null);
    }

    // Check if we're looking for a specific rocket (rockets/<rocket-name>)
    String componentPath = healthIndicatorMetadata.healthIndicatorConfiguration().id;
    String[] segments = (componentPath == null ? "" : componentPath).split("/", -1);
    String rocketName = segments.length > 1 ? segments[1] : null;

    if (rocketName != null && !rocketName.isEmpty()) {
      if (!results.containsKey(rocketName)) {
        throw new IllegalStateException("Rocket \"" + rocketName + "\" not found");
      }
      return new HealthIndicatorsResult(statusOf(results.get(rocketName)), null, rocketName, ROCKETS_ID + "/" + rocketName,
          null);
    }

    List<HealthIndicatorsResult> components = new ArrayList<>();
    for (Map.Entry<String, Boolean> rocket : results.entrySet()) {
      components.add(new HealthIndicatorsResult(statusOf(rocket.getValue()), null, rocket.getKey(),
          ROCKETS_ID + "/" + rocket.getKey(), null));
    }
    return new HealthIndicatorsResult(overallHealthStatus(results), null, "Rockets", ROCKETS_ID, components);
  }

  private static HealthStatus statusOf(Boolean up) {
    return Boolean.TRUE.equals(up) ? HealthStatus.UP : HealthStatus.DOWN;
  }

  private static HealthStatus overallHealthStatus(Map<String, Boolean> results) {
    boolean allUp = results.values().stream().allMatch(Boolean.TRUE::equals);
    if (allUp) {
      return HealthStatus.UP;
    }
    boolean allDown = results.values().stream().noneMatch(Boolean.TRUE::equals);
    if (allDown) {
      return HealthStatus.DOWN;
    }
    return HealthStatus.PARTIALLY_UP;
  }
}
