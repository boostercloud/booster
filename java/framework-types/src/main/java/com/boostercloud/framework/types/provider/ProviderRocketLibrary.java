package com.boostercloud.framework.types.provider;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.rockets.RocketEnvelope;

/**
 * Rocket request translation of a provider. Port of {@code ProviderRocketLibrary}.
 */
public interface ProviderRocketLibrary {
  /**
   * Despite the plural name (kept from TypeScript) it returns one envelope.
   *
   * @param config the configuration
   * @param request the provider-native request
   * @return the envelope with the rocket function id
   */
  RocketEnvelope rawToEnvelopes(BoosterConfig config, Object request);
}
