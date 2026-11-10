package com.boostercloud.framework.types.instrumentation;

/**
 * Start or end of a traced invocation. Port of the numeric enum {@code TraceTypes} ({@code START = 0},
 * {@code END = 1}).
 */
public enum TraceTypes {
  /** {@code 0}: before the invocation; {@code onStart} is called. */
  START,
  /** {@code 1}: after the invocation; {@code onEnd} is called. */
  END
}
