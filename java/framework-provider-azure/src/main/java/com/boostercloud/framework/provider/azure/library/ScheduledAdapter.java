package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.helpers.JsValues.get;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.helpers.JsValues;
import com.boostercloud.framework.provider.azure.types.AzureFunctionRawRequest;
import com.boostercloud.framework.provider.azure.types.AzureFunctionTypes;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.provider.azure.types.TimerInfo;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ScheduledCommandEnvelope;

/** Port of {@code library/scheduled-adapter.ts}: timer inputs to scheduled command envelopes. */
public final class ScheduledAdapter {

  private ScheduledAdapter() {}

  /**
   * Port of {@code ScheduledCommandInput}: {@code { timer, context, typeName }} of a {@code scheduleFunction-<Command>}
   * function.
   *
   * @param timer the timer information
   * @param context the invocation context
   * @param typeName the scheduled command name
   */
  public record ScheduledCommandInput(TimerInfo timer, AzureInvocationContext context, String typeName)
      implements AzureFunctionRawRequest {}

  /**
   * {@code isScheduledCommandInput}: identical to {@link AzureFunctionTypes#isTimerFunctionInput(Object)}; it does not
   * check {@code typeName} (quirk Q15).
   *
   * @param input any value
   * @return whether it is a timer input
   */
  public static boolean isScheduledCommandInput(Object input) {
    return AzureFunctionTypes.isTimerFunctionInput(input);
  }

  /**
   * {@code rawScheduledInputToEnvelope}: {@code { requestID: <random UUID>, typeName }}.
   *
   * @param config the configuration
   * @param input the timer input
   * @return the envelope
   * @throws IllegalStateException when the input is not a timer input
   */
  public static ScheduledCommandEnvelope rawScheduledInputToEnvelope(BoosterConfig config, Object input) {
    Logger logger = Loggers.getLogger(config, "scheduled-adapter#rawScheduledInputToEnvelope");
    logger.debug("Received AzureScheduledCommand request: ", input);

    if (!isScheduledCommandInput(input)) {
      throw new IllegalStateException("Invalid scheduled command input. Expected ScheduledCommandInput with typeName "
          + "and timer, but received: " + JsValues.stringify(input));
    }

    Object typeName = get(input, "typeName");
    Object isPastDue = get(get(input, "timer"), "isPastDue");
    logger.debug("Processing scheduled command: " + (typeName == null ? "undefined" : typeName) + ", isPastDue: "
        + isPastDue);

    return ScheduledCommandEnvelope.builder()
        .requestID(UUID.generate())
        .typeName(typeName == null ? null : typeName.toString())
        .build();
  }
}
