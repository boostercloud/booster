package com.boostercloud.framework.types.provider;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.ScheduledCommandEnvelope;

/**
 * Scheduled command triggers of a provider. Port of {@code ScheduledCommandsLibrary}.
 */
public interface ScheduledCommandsLibrary {
  /**
   * @param config the configuration
   * @param rawMessage the provider-native trigger
   * @return the envelope ({@code requestID}, {@code typeName})
   */
  ScheduledCommandEnvelope rawToEnvelope(BoosterConfig config, Object rawMessage);
}
