package com.boostercloud.framework.types.sensor;

/**
 * Ids of the built-in health indicators. Port of {@code BOOSTER_HEALTH_INDICATORS_IDS}; {@link #id()} is the id
 * string, also the key of {@code config.sensorConfiguration.health.booster}.
 */
public enum BoosterHealthIndicatorsIds {
  /** {@code booster}. */
  ROOT("booster"),
  /** {@code booster/function}. */
  FUNCTION("booster/function"),
  /** {@code booster/database}. */
  DATABASE("booster/database"),
  /** {@code booster/database/events}. */
  DATABASE_EVENTS("booster/database/events"),
  /** {@code booster/database/readmodels}. */
  DATABASE_READ_MODELS("booster/database/readmodels"),
  /** {@code rockets}. */
  ROCKETS("rockets");

  private final String id;

  BoosterHealthIndicatorsIds(String id) {
    this.id = id;
  }

  /**
   * @return the indicator id
   */
  public String id() {
    return id;
  }

  /**
   * @param id an indicator id
   * @return the built-in indicator with that id
   * @throws IllegalArgumentException when it is not a built-in id
   */
  public static BoosterHealthIndicatorsIds fromId(String id) {
    for (BoosterHealthIndicatorsIds value : values()) {
      if (value.id.equals(id)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unknown Booster health indicator '" + id + "'");
  }

  @Override
  public String toString() {
    return id;
  }
}
