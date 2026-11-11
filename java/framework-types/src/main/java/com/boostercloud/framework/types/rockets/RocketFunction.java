package com.boostercloud.framework.types.rockets;

import com.boostercloud.framework.types.BoosterConfig;

/**
 * A function contributed by a rocket and registered with {@code config.registerRocketFunction(id, function)}. Port of
 * {@code RocketFunction = (config, request) => Promise<unknown>}.
 */
@FunctionalInterface
public interface RocketFunction {
  /**
   * @param config the application configuration
   * @param request the provider-native request
   * @return the provider-native response
   */
  Object apply(BoosterConfig config, Object request);
}
