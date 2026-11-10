package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @ScheduledCommand(schedule)} decorator: registers the class in
 * {@code config.scheduledCommandHandlers} with its cron-like schedule. Every field is optional; {@code ""} means
 * absent ({@code null} in {@link com.boostercloud.framework.types.ScheduleInterface}). The class must declare
 * {@code public static void handle(Register register)}.
 *
 * <p>Registration error: {@code A command called <Name> is already registered.} + clean-build hint (the TypeScript
 * wording).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ScheduledCommand {
  /**
   * @return cron minute, e.g. {@code "0/5"}
   */
  String minute() default "";

  /**
   * @return cron hour
   */
  String hour() default "";

  /**
   * @return cron day of month
   */
  String day() default "";

  /**
   * @return cron month
   */
  String month() default "";

  /**
   * @return cron day of week
   */
  String weekDay() default "";

  /**
   * @return cron year
   */
  String year() default "";
}
