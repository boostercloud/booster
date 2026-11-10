package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @GlobalEventHandler} decorator: a handler that receives every event. Registered under the
 * {@link #BOOSTER_GLOBAL_EVENT_HANDLERS} key of {@code config.eventHandlers}. The class must declare
 * {@code public static void handle(Object event, Register register)}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GlobalEventHandler {
  /** Key of the global handlers in {@code config.eventHandlers}. */
  String BOOSTER_GLOBAL_EVENT_HANDLERS = "BOOSTER_GLOBAL_EVENT_HANDLERS";
}
