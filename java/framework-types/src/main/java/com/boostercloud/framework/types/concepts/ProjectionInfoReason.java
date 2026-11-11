package com.boostercloud.framework.types.concepts;

/**
 * Why a projection runs. Port of the numeric enum {@code ProjectionInfoReason}; {@link #value()} keeps the numbers.
 */
public enum ProjectionInfoReason {
  /** {@code 0}: the entity changed. */
  ENTITY_PROJECTED(0),
  /** {@code 1}: the entity was deleted (an un-projection runs). */
  ENTITY_DELETED(1);

  private final int value;

  ProjectionInfoReason(int value) {
    this.value = value;
  }

  /**
   * @return the TypeScript numeric value
   */
  public int value() {
    return value;
  }
}
