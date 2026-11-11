package com.boostercloud.framework.core.instrumentation;

import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.instrumentation.TraceConfiguration;
import com.boostercloud.framework.types.instrumentation.TraceHandler;
import com.boostercloud.framework.types.instrumentation.TraceInfo;
import com.boostercloud.framework.types.instrumentation.TraceTypes;
import java.util.Arrays;
import java.util.Collection;

/**
 * Port of {@code instrumentation/trace-notifier.ts} ({@code notifyTrace}, {@code isTraceEnabled}).
 */
public final class TraceNotifier {

  private TraceNotifier() {}

  /**
   * Calls {@code traceConfiguration.onStart} for {@link TraceTypes#START} and {@code onEnd} otherwise, with
   * {@code (config, actionType, parameters)}. A {@code null} handler does nothing (TypeScript would fail calling
   * {@code undefined}).
   *
   * @param type start or end
   * @param actionType the action ({@code COMMAND_HANDLER}, a custom string, ...)
   * @param parameters the trace information, the same object for start and end
   * @param config the configuration whose {@code traceConfiguration} is used
   */
  public static void notifyTrace(TraceTypes type, String actionType, TraceInfo parameters, BoosterConfig config) {
    TraceConfiguration traceConfiguration = config.traceConfiguration;
    TraceHandler handler = type == TraceTypes.START ? traceConfiguration.onStart : traceConfiguration.onEnd;
    if (handler != null) {
      handler.handle(config, actionType, parameters);
    }
  }

  /**
   * {@code isTraceEnabled(actionType, config)}: {@code false} when there is no configuration, no
   * {@code traceConfiguration}, a falsy {@code enableTraceNotification}, or a list of action types that does not
   * contain {@code actionType}; {@code true} otherwise ({@code true} or a list containing it).
   *
   * @param actionType the action
   * @param config the configuration, or {@code null}
   * @return whether the action is traced
   */
  public static boolean isTraceEnabled(String actionType, BoosterConfig config) {
    if (config == null || config.traceConfiguration == null) {
      return false;
    }
    Object enabled = config.traceConfiguration.enableTraceNotification;
    if (!Js.isTruthy(enabled)) {
      return false;
    }
    return switch (enabled) {
      case Collection<?> actions -> actions.contains(actionType);
      case Object[] actions -> Arrays.asList(actions).contains(actionType);
      default -> true;
    };
  }
}
