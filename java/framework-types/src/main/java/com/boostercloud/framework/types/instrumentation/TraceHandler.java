package com.boostercloud.framework.types.instrumentation;

import com.boostercloud.framework.types.BoosterConfig;

/**
 * A trace callback ({@code onStart} / {@code onEnd}). Port of {@code TraceHandler}.
 */
@FunctionalInterface
public interface TraceHandler {
  /** A handler that does nothing (the default {@code async () => {}}). */
  TraceHandler NOOP = (config, actionType, traceInfo) -> {};

  /**
   * @param config the application configuration
   * @param actionType the traced action ({@link TraceActionTypes} name or a custom string)
   * @param traceInfo the invocation information
   */
  void handle(BoosterConfig config, String actionType, TraceInfo traceInfo);
}
