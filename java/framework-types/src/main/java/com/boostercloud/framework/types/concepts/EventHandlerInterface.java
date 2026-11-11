package com.boostercloud.framework.types.concepts;

/**
 * Port of {@code EventHandlerInterface}. Event handlers are classes with a <b>static</b> method
 * {@code public static void handle(MyEvent event, Register register)} (Java interfaces cannot require static methods),
 * registered in {@code config.eventHandlers[eventName]} as classes. Implementing this marker is optional.
 */
public interface EventHandlerInterface {
  /** Name of the static handler method core invokes. */
  String HANDLE_METHOD = "handle";
}
