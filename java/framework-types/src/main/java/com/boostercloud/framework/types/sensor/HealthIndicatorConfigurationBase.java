package com.boostercloud.framework.types.sensor;

import java.util.Objects;

/**
 * Settings of one health indicator. Port of {@code HealthIndicatorConfigurationBase} (also used for TypeScript's
 * alias {@code SensorBoosterHealthConfigurationDetails}). Mutable: applications enable indicators in place
 * ({@code config.sensorConfiguration.health.booster.values().forEach(i -> i.enabled = true)}).
 */
public class HealthIndicatorConfigurationBase {
  /** Whether the indicator is evaluated. */
  public boolean enabled;

  /** Whether details are included in the response. */
  public boolean details;

  /** Whether children are included in the response; optional ({@code null}). */
  public Boolean showChildren;

  /** Creates a disabled configuration without details. */
  public HealthIndicatorConfigurationBase() {}

  /**
   * @param enabled whether the indicator is evaluated
   * @param details whether details are shown
   * @param showChildren whether children are shown, or {@code null}
   */
  public HealthIndicatorConfigurationBase(boolean enabled, boolean details, Boolean showChildren) {
    this.enabled = enabled;
    this.details = details;
    this.showChildren = showChildren;
  }

  /**
   * @return an independent copy
   */
  public HealthIndicatorConfigurationBase copy() {
    return new HealthIndicatorConfigurationBase(enabled, details, showChildren);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof HealthIndicatorConfigurationBase that
        && getClass() == that.getClass()
        && enabled == that.enabled
        && details == that.details
        && Objects.equals(showChildren, that.showChildren);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, details, showChildren);
  }

  @Override
  public String toString() {
    return "{enabled=" + enabled + ", details=" + details + ", showChildren=" + showChildren + "}";
  }
}
