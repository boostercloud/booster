package com.boostercloud.integration.common;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.instrumentation.TraceInfo;
import com.boostercloud.framework.types.instrumentation.TraceTypes;

/** The trace callbacks of the application ({@code src/common/custom-tracer.ts}); they log at debug level. */
public final class CustomTracer {

  private CustomTracer() {}

  /**
   * Logs {@code START: <actionType>}.
   *
   * @param config the configuration
   * @param actionType the traced action
   * @param traceInfo the trace information
   */
  public static void onStart(BoosterConfig config, String actionType, TraceInfo traceInfo) {
    Loggers.getLogger(config, "CustomTracer#onStart").debug(TraceTypes.START.name() + ": " + actionType, traceInfo);
  }

  /**
   * Logs {@code END: <actionType> (<elapsed> ms)}.
   *
   * @param config the configuration
   * @param actionType the traced action
   * @param traceInfo the trace information
   */
  public static void onEnd(BoosterConfig config, String actionType, TraceInfo traceInfo) {
    Loggers.getLogger(config, "CustomTracer#onEnd").debug(TraceTypes.END.name() + ": " + actionType + " ("
        + traceInfo.elapsedInvocationMillis + " ms)\n", traceInfo);
  }
}
