package com.boostercloud.framework.types.envelope;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Port of {@code SuperKindType = 'domain' | 'notification' | 'booster'}, serialized with those literals.
 * {@code notification} exists for compatibility with stored data; no TypeScript producer assigns it.
 */
public enum SuperKindType {
  /** {@code 'domain'}: user events. */
  DOMAIN("domain"),
  /** {@code 'notification'}. */
  NOTIFICATION("notification"),
  /** {@code 'booster'}: framework events. */
  BOOSTER("booster");

  private final String value;

  SuperKindType(String value) {
    this.value = value;
  }

  /**
   * @return the persisted literal
   */
  @JsonValue
  public String value() {
    return value;
  }

  /**
   * @param value a persisted literal
   * @return the super kind
   */
  @JsonCreator
  public static SuperKindType fromValue(String value) {
    for (SuperKindType kind : values()) {
      if (kind.value.equals(value)) {
        return kind;
      }
    }
    throw new IllegalArgumentException("Unknown super kind '" + value + "'");
  }

  @Override
  public String toString() {
    return value;
  }
}
