package com.boostercloud.framework.core.sensor.health;

import com.boostercloud.framework.core.BoosterAuthorizer;
import com.boostercloud.framework.core.BoosterTokenVerifier;
import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.core.sensor.health.healthindicators.DefaultBoosterHealthIndicators;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.concepts.HealthAuthorizer;
import com.boostercloud.framework.types.envelope.HealthEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.sensor.BoosterHealthIndicatorsIds;
import com.boostercloud.framework.types.sensor.HealthIndicatorConfiguration;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthStatus;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code sensor/health/booster-health-service.ts}: the entry point of the health endpoint (request →
 * envelope → authentication/authorization → selected indicators → resolved tree → aggregated status → provider
 * response). Never throws to the provider: failures go through {@code provider.api().requestFailed}.
 *
 * <ol>
 *   <li>{@code provider.sensor().rawRequestToHealthEnvelope(request)};</li>
 *   <li>a present token is verified ({@link BoosterTokenVerifier}; an invalid token fails the request even when every
 *       user is authorized), then {@code sensorConfiguration.health.globalAuthorizer} runs;</li>
 *   <li>the registry is {@code config.userHealthIndicators}, or the built-in indicators rebuilt from the current
 *       {@code sensorConfiguration} when no {@code @HealthSensor} exists;</li>
 *   <li>the start set: every root indicator for an empty component path; for {@code rockets/<name>} a copy of the
 *       {@code rockets} indicator whose id is the full path ({@code Rockets health provider not found} without it);
 *       otherwise the indicator with that id ({@code Unexpected HealthProvider id <path>});</li>
 *   <li>depth-first, in registry order: disabled indicators (or with a disabled registered ancestor) are left out;
 *       each indicator is a new instance of its class (no-argument constructor) whose {@code health(config,
 *       metadata)} runs; {@code details} is removed when the indicator's {@code details} flag is off; the node gets
 *       the configured {@code name} (unless the result already has a {@code name} and an {@code id}) and the
 *       configured {@code id}, and {@code components} when it has visible children ({@link HealthUtils});</li>
 *   <li>healthy iff every node of the response is {@code UP}, except a {@code rockets} node that is
 *       {@code UNKNOWN};</li>
 *   <li>{@code provider.api().healthRequestResult(nodes, isHealthy)}; any exception goes to
 *       {@code provider.api().requestFailed(error)}.</li>
 * </ol>
 *
 * <p>The response nodes are raw JSON objects ({@code Map<String, Object>}) built like the TypeScript object spread
 * ({@code { ...indicatorResult, name, id, components }}), so whatever an indicator returns (a
 * {@code HealthIndicatorResult}, a {@code HealthIndicatorsResult}, any serialisable object) keeps its properties.
 */
public class BoosterHealthService {

  private static final String HEALTH_METHOD = "health";

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public BoosterHealthService(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code boosterHealth(request)}.
   *
   * @param request the provider-specific request
   * @return the provider-specific response
   */
  public Object boosterHealth(Object request) {
    try {
      HealthEnvelope healthEnvelope = config.provider().sensor().rawRequestToHealthEnvelope(request);
      validate(healthEnvelope);
      Map<String, HealthIndicatorMetadata> healthProviders = getHealthProviders();
      List<HealthIndicatorMetadata> parents = parentsHealthProviders(healthEnvelope, healthProviders);
      List<Map<String, Object>> healthIndicatorResults = boosterHealthProviderResolver(parents, healthProviders);

      // Check if all components are healthy (considering UNKNOWN rockets as healthy)
      boolean isHealthy = isOverallHealthy(healthIndicatorResults);

      return config.provider().api().healthRequestResult(healthIndicatorResults, isHealthy);
    } catch (Exception e) {
      return config.provider().api().requestFailed(e);
    }
  }

  private void validate(HealthEnvelope healthEnvelope) {
    UserEnvelope userEnvelope = verify(healthEnvelope);
    HealthAuthorizer authorizer = BoosterAuthorizer.build(config.sensorConfiguration.health.globalAuthorizer);
    authorizer.authorize(userEnvelope, healthEnvelope);
  }

  private UserEnvelope verify(HealthEnvelope envelope) {
    BoosterTokenVerifier boosterTokenVerifier = new BoosterTokenVerifier(config);
    String token = envelope.token();
    if (!Js.isTruthy(token)) {
      return null;
    }
    return boosterTokenVerifier.verify(token);
  }

  private List<Map<String, Object>> boosterHealthProviderResolver(List<HealthIndicatorMetadata> healthIndicatorsMetadata,
      Map<String, HealthIndicatorMetadata> healthProviders) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (HealthIndicatorMetadata current : healthIndicatorsMetadata) {
      Map<String, Object> indicatorResult = enabledIndicatorHealth(current, healthProviders);
      if (indicatorResult == null) {
        continue;
      }
      List<HealthIndicatorMetadata> children = HealthUtils.childHealthProviders(current, healthProviders);

      // Check if the result is already a HealthIndicatorsResult (has name and id)
      boolean isHealthIndicatorsResult = indicatorResult.containsKey("name") && indicatorResult.containsKey("id");

      Map<String, Object> newResult = new LinkedHashMap<>(indicatorResult);
      // Only use the configuration name if we don't already have a name (for individual rocket checks)
      newResult.put("name", isHealthIndicatorsResult ? indicatorResult.get("name") : current.healthIndicatorConfiguration().name);
      newResult.put("id", current.healthIndicatorConfiguration().id);
      if (!children.isEmpty()) {
        newResult.put("components", boosterHealthProviderResolver(children, healthProviders));
      }
      result.add(newResult);
    }
    return result;
  }

  private Map<String, Object> enabledIndicatorHealth(HealthIndicatorMetadata current,
      Map<String, HealthIndicatorMetadata> healthProviders) {
    if (HealthUtils.isEnabled(current, healthProviders)) {
      return indicatorHealth(current);
    }
    return null;
  }

  private Map<String, Object> indicatorHealth(HealthIndicatorMetadata metadata) {
    Class<?> rootClass = metadata.type();
    Object instance = UserCode.newInstance(rootClass);
    Method health = UserCode.findInstance(rootClass, HEALTH_METHOD, config, metadata)
        .orElseThrow(() -> new IllegalStateException("instance.health is not a function: the health indicator "
            + rootClass.getName() + " must declare a public instance method \"health(BoosterConfig config,"
            + " HealthIndicatorMetadata metadata)\""));
    Object healthIndicatorResult = UserCode.invoke(health, instance, config, metadata);
    if (healthIndicatorResult == null) {
      if (!metadata.healthIndicatorConfiguration().details) {
        throw new IllegalStateException("Cannot set properties of undefined (setting 'details'): the health indicator "
            + rootClass.getName() + " returned no result");
      }
      return null;
    }
    Map<String, Object> rawResult = new LinkedHashMap<>(BoosterJson.toRawMap(healthIndicatorResult));
    if (!metadata.healthIndicatorConfiguration().details) {
      rawResult.remove("details");
    }
    return rawResult;
  }

  /** If there is not any indicator configured, then we will use only the Booster indicators. */
  private Map<String, HealthIndicatorMetadata> getHealthProviders() {
    return config.userHealthIndicators != null && !config.userHealthIndicators.isEmpty()
        ? config.userHealthIndicators
        : DefaultBoosterHealthIndicators.defaultBoosterHealthIndicators(config);
  }

  private List<HealthIndicatorMetadata> parentsHealthProviders(HealthEnvelope envelope,
      Map<String, HealthIndicatorMetadata> healthProviders) {
    String componentPath = envelope.componentPath();
    if (componentPath == null || componentPath.isEmpty()) {
      return HealthUtils.rootHealthProviders(healthProviders);
    }

    // Special handling for rockets - always use the root rockets provider
    if (componentPath.startsWith(BoosterHealthIndicatorsIds.ROCKETS.id() + HealthUtils.PATH_SEPARATOR)) {
      HealthIndicatorMetadata rocketsProvider = healthProviders.get(BoosterHealthIndicatorsIds.ROCKETS.id());
      if (rocketsProvider == null) {
        throw new IllegalStateException("Rockets health provider not found");
      }
      // Pass the full path in the configuration so RocketsHealthIndicator can handle it
      HealthIndicatorConfiguration configuration = rocketsProvider.healthIndicatorConfiguration().copy();
      configuration.id = componentPath;
      return List.of(new HealthIndicatorMetadata(rocketsProvider.type(), configuration));
    }

    // Normal handling for other health providers
    return List.of(HealthUtils.metadataFromId(healthProviders, componentPath));
  }

  @SuppressWarnings("unchecked")
  private boolean isOverallHealthy(List<Map<String, Object>> results) {
    for (Map<String, Object> result : results) {
      Object status = result.get("status");
      // Special case: UNKNOWN status for rockets is considered healthy
      if (BoosterHealthIndicatorsIds.ROCKETS.id().equals(result.get("id"))
          && HealthStatus.UNKNOWN.name().equals(status)) {
        continue;
      }

      // Check current component's status
      if (!HealthStatus.UP.name().equals(status)) {
        return false;
      }

      // Recursively check child components if they exist
      if (result.get("components") instanceof List<?> components && !components.isEmpty()) {
        if (!isOverallHealthy((List<Map<String, Object>>) components)) {
          return false;
        }
      }
    }
    return true;
  }
}
