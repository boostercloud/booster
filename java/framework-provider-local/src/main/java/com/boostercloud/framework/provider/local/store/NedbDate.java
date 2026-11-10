package com.boostercloud.framework.provider.local.store;

import com.boostercloud.framework.types.Dates;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Instant;
import java.util.Date;

/**
 * A JavaScript {@code Date} stored in a NeDB document. NeDB persists dates as {@code {"$$date": <epoch millis>}} and
 * gives them their own place in its cross-type ordering (after booleans, before arrays) and comparison rules. Booster
 * itself stores timestamps as ISO strings, so dates only appear in documents written by other tools or in documents
 * that contain {@link Instant}/{@link Date} values.
 *
 * <p>When a document is converted with {@code BoosterJson} a date becomes its ISO string, like
 * {@code JSON.stringify(date)}.
 *
 * @param epochMillis milliseconds since the epoch ({@code date.getTime()}); {@code NaN} is an invalid date
 */
public record NedbDate(double epochMillis) {

  /**
   * @param instant an instant
   * @return the date (millisecond precision, like JavaScript)
   */
  public static NedbDate of(Instant instant) {
    return new NedbDate(instant.toEpochMilli());
  }

  /**
   * @param date a {@link Date}
   * @return the date
   */
  public static NedbDate of(Date date) {
    return new NedbDate(date.getTime());
  }

  /**
   * @return whether this is a valid date ({@code !isNaN(date.getTime())})
   */
  public boolean isValid() {
    return !Double.isNaN(epochMillis);
  }

  /**
   * {@code date.toJSON()}: the ISO string, or {@code null} for an invalid date.
   *
   * @return the ISO string
   */
  @JsonValue
  public String toIsoString() {
    return isValid() ? Dates.toIso((long) epochMillis) : null;
  }

  @Override
  public String toString() {
    return isValid() ? toIsoString() : "Invalid Date";
  }
}
