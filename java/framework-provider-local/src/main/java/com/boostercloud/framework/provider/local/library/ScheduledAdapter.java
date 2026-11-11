package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.local.store.Js;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ScheduledCommandEnvelope;
import java.util.Map;

/**
 * Scheduled command triggers. Port of {@code library/scheduled-adapter.ts}.
 */
public final class ScheduledAdapter {

  private ScheduledAdapter() {}

  /**
   * The trigger the local scheduler sends: {@code { typeName }}. Port of {@code LocalScheduleCommandEnvelope}.
   *
   * @param typeName the scheduled command name
   */
  public record LocalScheduleCommandEnvelope(String typeName) {}

  /**
   * {@code rawScheduledInputToEnvelope(config, input)}.
   *
   * @param config the configuration
   * @param input a {@link LocalScheduleCommandEnvelope} or a map {@code { typeName }}
   * @return {@code { requestID: <new uuid>, typeName }}
   * @throws IllegalArgumentException {@code typeName is not defined or empty, scheduled command envelope should have
   *     the structure {typeName: string }, but you gave <JSON>}
   */
  public static ScheduledCommandEnvelope rawScheduledInputToEnvelope(BoosterConfig config, Object input) {
    Logger logger = Loggers.getLogger(config, "rawScheduledInputToEnvelope");
    logger.debug("Received LocalScheduleCommand request: ", input);
    Object typeName = input instanceof LocalScheduleCommandEnvelope envelope
        ? envelope.typeName()
        : input instanceof Map<?, ?> map ? map.get("typeName") : null;
    if (!Js.truthy(typeName)) {
      throw new IllegalArgumentException("typeName is not defined or empty, scheduled command envelope should have the "
          + "structure {typeName: string }, but you gave " + (input == null ? "undefined" : BoosterJson.toJson(input)));
    }
    return ScheduledCommandEnvelope.builder().requestID(UUID.generate()).typeName(Js.toJsString(typeName)).build();
  }
}
