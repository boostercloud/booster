package com.boostercloud.framework.types.instrumentation;

import java.util.Collection;
import java.util.List;

/**
 * Tracing settings ({@code config.traceConfiguration}). Port of {@code TraceConfiguration}.
 *
 * <p>{@link #enableTraceNotification} keeps the TypeScript union {@code boolean | Array<string>}: assign
 * {@code true}/{@code false} or a list of action names (or use {@link #enableAll()} / {@link #enableFor(String...)}).
 * {@link #isTraceEnabledFor(String)} applies the TypeScript rules: {@code false} or {@code null} traces nothing,
 * {@code true} traces everything, a list traces the listed actions (an empty list traces nothing).
 */
public class TraceConfiguration {
  /** {@code Boolean} or {@code Collection<String>} of action names; default {@code false}. */
  public Object enableTraceNotification = Boolean.FALSE;

  /** Whether {@link TraceInfo#internal} is filled. */
  public boolean includeInternal = false;

  /** Called before traced invocations. */
  public TraceHandler onStart = TraceHandler.NOOP;

  /** Called after traced invocations (also when they fail). */
  public TraceHandler onEnd = TraceHandler.NOOP;

  /**
   * Traces every action.
   *
   * @return this configuration
   */
  public TraceConfiguration enableAll() {
    enableTraceNotification = Boolean.TRUE;
    return this;
  }

  /**
   * Traces only the given actions.
   *
   * @param actionTypes action names
   * @return this configuration
   */
  public TraceConfiguration enableFor(String... actionTypes) {
    enableTraceNotification = List.of(actionTypes);
    return this;
  }

  /**
   * Disables tracing.
   *
   * @return this configuration
   */
  public TraceConfiguration disable() {
    enableTraceNotification = Boolean.FALSE;
    return this;
  }

  /**
   * TypeScript's {@code isTraceEnabled}: whether an action is traced.
   *
   * @param actionType the action name
   * @return whether it is traced
   * @throws IllegalStateException when {@link #enableTraceNotification} holds something else than a boolean or a
   *     collection
   */
  public boolean isTraceEnabledFor(String actionType) {
    return switch (enableTraceNotification) {
      case null -> false;
      case Boolean enabled -> enabled;
      case Collection<?> actions -> actions.contains(actionType);
      default -> throw new IllegalStateException(
          "traceConfiguration.enableTraceNotification must be a boolean or a list of action names, but it is a "
              + enableTraceNotification.getClass().getName());
    };
  }
}
