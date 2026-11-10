package com.boostercloud.framework.provider.azure.library;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.rockets.RocketEnvelope;
import com.boostercloud.framework.types.rockets.Rockets;

/** Port of {@code library/rocket-adapter.ts}. */
public final class RocketAdapter {

  private RocketAdapter() {}

  /**
   * {@code rawRocketInputToEnvelope}: the rocket id comes from the function app setting
   * {@code BOOSTER_ROCKET_FUNCTION_ID} (each rocket has its own function app); both arguments are ignored.
   *
   * @param config ignored
   * @param request ignored
   * @return {@code { rocketId }} ({@code null} when the setting is missing)
   */
  public static RocketEnvelope rawRocketInputToEnvelope(BoosterConfig config, Object request) {
    return new RocketEnvelope(EnvironmentVariables.get(Rockets.ROCKET_FUNCTION_ID_ENV_VAR).orElse(null));
  }
}
