package com.boostercloud.framework.types.sensor;

import java.util.Objects;

/**
 * Settings of a user health indicator ({@code @HealthSensor}). Port of {@code HealthIndicatorConfiguration}.
 */
public class HealthIndicatorConfiguration extends HealthIndicatorConfigurationBase {
  /** The indicator id: a slash-separated path; children are found by path prefix. */
  public String id;

  /** The indicator name. */
  public String name;

  /** Creates an empty configuration. */
  public HealthIndicatorConfiguration() {}

  /**
   * @param id the indicator id
   * @param name the indicator name
   * @param enabled whether the indicator is evaluated
   * @param details whether details are shown
   * @param showChildren whether children are shown, or {@code null}
   */
  public HealthIndicatorConfiguration(String id, String name, boolean enabled, boolean details, Boolean showChildren) {
    super(enabled, details, showChildren);
    this.id = id;
    this.name = name;
  }

  @Override
  public HealthIndicatorConfiguration copy() {
    return new HealthIndicatorConfiguration(id, name, enabled, details, showChildren);
  }

  @Override
  public boolean equals(Object other) {
    return super.equals(other)
        && other instanceof HealthIndicatorConfiguration that
        && Objects.equals(id, that.id)
        && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), id, name);
  }

  @Override
  public String toString() {
    return "{id=" + id + ", name=" + name + ", enabled=" + enabled + ", details=" + details
        + ", showChildren=" + showChildren + "}";
  }
}
