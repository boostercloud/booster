package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @EventHandler(EventClass)} decorator: appends the class to {@code config.eventHandlers[<Event>]}
 * (an identical class is not added twice). The class must declare
 * {@code public static void handle(TheEvent event, Register register)}. Works for events and notifications.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(EventHandler.List.class)
public @interface EventHandler {
  /**
   * @return the handled event or notification class
   */
  Class<?> value();

  /** Container of repeated {@link EventHandler}. */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface List {
    /**
     * @return the annotations
     */
    EventHandler[] value();
  }
}
