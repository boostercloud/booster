package com.boostercloud.framework.types.sensor;

/**
 * Sensor settings ({@code config.sensorConfiguration}). Port of {@code SensorConfiguration}.
 */
public class SensorConfiguration {
  /** Health sensor settings. */
  public final SensorBoosterHealthConfiguration health = new SensorBoosterHealthConfiguration();
}
