package com.boostercloud.framework.types.concepts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status of a data migration. Port of the numeric enum {@code DataMigrationStatus}: it is <b>persisted as a number</b>
 * ({@code 0}/{@code 1}) in the {@code BoosterDataMigrationEntity} snapshots, so it serializes as its
 * {@link #value()}.
 */
public enum DataMigrationStatus {
  /** {@code 0}. */
  RUNNING(0),
  /** {@code 1}. */
  FINISHED(1);

  private final int value;

  DataMigrationStatus(int value) {
    this.value = value;
  }

  /**
   * @return the persisted numeric value
   */
  @JsonValue
  public int value() {
    return value;
  }

  /**
   * Reads the persisted value; accepts the number (also as text, {@code "1"}) or the name.
   *
   * @param value {@code 0}, {@code 1}, {@code "0"}, {@code "1"}, {@code "RUNNING"} or {@code "FINISHED"}
   * @return the status
   */
  @JsonCreator
  public static DataMigrationStatus fromValue(Object value) {
    if (value instanceof Number number) {
      return fromNumber(number.intValue());
    }
    String text = String.valueOf(value).trim();
    for (DataMigrationStatus status : values()) {
      if (status.name().equalsIgnoreCase(text)) {
        return status;
      }
    }
    try {
      return fromNumber(Integer.parseInt(text));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Unknown data migration status '" + value + "'", e);
    }
  }

  private static DataMigrationStatus fromNumber(int number) {
    for (DataMigrationStatus status : values()) {
      if (status.value == number) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown data migration status " + number);
  }

  /**
   * @return the number as text, like TypeScript's {@code status.toString()} ({@code "0"}/{@code "1"})
   */
  @Override
  public String toString() {
    return Integer.toString(value);
  }
}
