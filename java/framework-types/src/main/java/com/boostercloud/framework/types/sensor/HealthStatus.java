package com.boostercloud.framework.types.sensor;

/**
 * Status of a health indicator. Port of the string enum {@code HealthStatus}; the constant names are the wire values.
 */
public enum HealthStatus {
  /** The component works as expected. */
  UP,
  /** The component works partially or with reduced functionality. */
  PARTIALLY_UP,
  /** The component does not work. */
  DOWN,
  /** The component is temporarily out of service. */
  OUT_OF_SERVICE,
  /** The component state is unknown. */
  UNKNOWN
}
