package com.boostercloud.framework.core;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.rockets.RocketEnvelope;
import com.boostercloud.framework.types.rockets.RocketFunction;

/**
 * Port of {@code booster-rocket-dispatcher.ts}: the entry point of rocket functions. The rocket id comes from
 * {@code provider.rockets().rawToEnvelopes}; the function registered with {@code config.registerRocketFunction}
 * receives the configuration and the <b>raw</b> request, and its result is returned.
 */
public class BoosterRocketDispatcher {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public BoosterRocketDispatcher(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code dispatch(request)}.
   *
   * @param request the provider-specific request
   * @return the rocket function's result
   * @throws IllegalStateException {@code Attempt to execute a rocket function but the ID is missing.} or
   *     {@code Rocket function with ID "<id>" not found. Did you forget registering the function with
   *     "config.registerRocketFunction()"?}
   */
  public Object dispatch(Object request) {
    RocketEnvelope rawToEnvelopes = config.provider().rockets().rawToEnvelopes(config, request);
    String rocketId = rawToEnvelopes == null ? null : rawToEnvelopes.rocketId();
    if (rocketId == null || rocketId.isEmpty()) {
      throw new IllegalStateException("Attempt to execute a rocket function but the ID is missing.");
    }
    RocketFunction rocketFunction = config.getRegisteredRocketFunction(rocketId)
        .orElseThrow(() -> new IllegalStateException("Rocket function with ID \"" + rocketId
            + "\" not found. Did you forget registering the function with \"config.registerRocketFunction()\"?"));
    return rocketFunction.apply(config, request);
  }
}
