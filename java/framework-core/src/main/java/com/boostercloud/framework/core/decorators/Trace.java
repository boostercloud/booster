package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @Trace(actionType = 'CUSTOM', description?)} method decorator. Java cannot intercept calls, so
 * the annotation takes effect on methods the <b>framework</b> calls (handlers, reducers, projections, hooks,
 * migrations, health indicators): the call is wrapped with
 * {@link com.boostercloud.framework.core.instrumentation.Tracer}, which notifies
 * {@code config.traceConfiguration.onStart}/{@code onEnd} when tracing is enabled for the action.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Trace {
  /**
   * @return the action type ({@code CUSTOM} by default; any string can be used in
   *     {@code enableTraceNotification})
   */
  String value() default "CUSTOM";

  /**
   * @return a description passed in {@code TraceInfo.description} ({@code ""} for none)
   */
  String description() default "";
}
