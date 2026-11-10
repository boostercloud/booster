package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.rockets.RocketEnvelope;
import com.boostercloud.framework.types.rockets.Rockets;
import java.util.Map;
import java.util.Objects;

/**
 * Rocket function requests. Port of {@code library/rocket-adapter.ts}.
 */
public final class RocketAdapter {

  private RocketAdapter() {}

  /**
   * {@code rawRocketInputToEnvelope(config, request)}: the rocket function id is the request's
   * {@code BOOSTER_ROCKET_FUNCTION_ID} property or, when it is absent ({@code ??}), the environment variable of the
   * same name.
   *
   * @param config the configuration
   * @param request the rocket request (a map, or an object serializable to one)
   * @return {@code { rocketId }} (possibly {@code null})
   */
  public static RocketEnvelope rawRocketInputToEnvelope(BoosterConfig config, Object request) {
    Objects.requireNonNull(request, "Cannot read properties of undefined (reading 'BOOSTER_ROCKET_FUNCTION_ID')");
    Object idFromRequest;
    if (request instanceof Map<?, ?> map) {
      idFromRequest = map.get(Rockets.ROCKET_FUNCTION_ID_ENV_VAR);
    } else {
      Object raw;
      try {
        raw = BoosterJson.toRaw(request);
      } catch (RuntimeException e) {
        raw = null;
      }
      idFromRequest = raw instanceof Map<?, ?> map ? map.get(Rockets.ROCKET_FUNCTION_ID_ENV_VAR) : null;
    }
    String id = idFromRequest != null
        ? String.valueOf(idFromRequest)
        : EnvironmentVariables.get(Rockets.ROCKET_FUNCTION_ID_ENV_VAR).orElse(null);
    return new RocketEnvelope(id);
  }
}
