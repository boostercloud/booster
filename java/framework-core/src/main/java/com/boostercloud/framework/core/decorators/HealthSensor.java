package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @HealthSensor(attributes)} decorator: registers a user health indicator in
 * {@code config.userHealthIndicators} under its {@link #id()}. The first registration seeds the map with the six
 * built-in indicators (with the settings of {@code config.sensorConfiguration.health.booster} at that moment). The
 * class must implement {@link com.boostercloud.framework.types.sensor.HealthIndicatorInterface} and have a
 * no-argument constructor.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HealthSensor {
  /**
   * @return the {@code /}-separated id, which places the indicator in the tree ({@code booster/database/mine})
   */
  String id();

  /**
   * @return the display name
   */
  String name();

  /**
   * @return whether the indicator (and its children) runs
   */
  boolean enabled();

  /**
   * @return whether its details are returned
   */
  boolean details();

  /**
   * @return whether children components are included
   */
  boolean showChildren() default true;
}
