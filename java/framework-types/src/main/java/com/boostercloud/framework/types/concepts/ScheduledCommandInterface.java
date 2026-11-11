package com.boostercloud.framework.types.concepts;

/**
 * Port of {@code ScheduledCommandInterface}: a class with a <b>static</b>
 * {@code public static void handle(Register register)} method. Implementing this marker is optional.
 */
public interface ScheduledCommandInterface {
  /** Name of the static handler method core invokes. */
  String HANDLE_METHOD = "handle";
}
