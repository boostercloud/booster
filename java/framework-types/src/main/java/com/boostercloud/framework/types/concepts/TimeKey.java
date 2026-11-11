package com.boostercloud.framework.types.concepts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.boostercloud.framework.types.Dates;
import java.util.Objects;

/**
 * Time-based unique identifier, the port of {@code class TimeKey extends String}: a string in the form
 * {@code <epoch milliseconds>-<random UUID>}. Like {@link UUID}, any string is accepted (tests use {@code "1"}).
 * Lexical order equals chronological order while the timestamp has 13 digits (2001-09-09 to 2286-11-20).
 *
 * @param value the key text
 */
public record TimeKey(@JsonValue String value) implements Comparable<TimeKey> {

  /** Canonical constructor; rejects {@code null}. */
  public TimeKey {
    Objects.requireNonNull(value, "A TimeKey value cannot be null");
  }

  /**
   * @param value any string
   * @return the key wrapping it
   */
  @JsonCreator
  public static TimeKey of(String value) {
    return new TimeKey(value);
  }

  /**
   * {@code TimeKey.generate()} with the current time of {@link Dates#clock()}.
   *
   * @return a new key
   */
  public static TimeKey generate() {
    return generate(Dates.nowEpochMillis());
  }

  /**
   * {@code TimeKey.generate(moment)}: {@code moment + "-" + uuid}. No padding or validation.
   *
   * @param moment milliseconds since the epoch
   * @return a new key
   */
  public static TimeKey generate(long moment) {
    return new TimeKey(moment + "-" + java.util.UUID.randomUUID());
  }

  @Override
  public int compareTo(TimeKey other) {
    return value.compareTo(other.value);
  }

  /**
   * @return the key text
   */
  @Override
  public String toString() {
    return value;
  }
}
