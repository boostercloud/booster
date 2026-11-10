package com.boostercloud.framework.types;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Date and time helpers with the exact formats of JavaScript's {@code Date}.
 *
 * <p>Every timestamp Booster persists or sends ({@code createdAt}, {@code persistedAt}, {@code lastUpdateAt}, ...) is
 * an ISO-8601 UTC string with exactly three fraction digits and a {@code Z} suffix, as produced by
 * {@code new Date().toISOString()} ({@code 2024-01-31T10:20:30.123Z}). Providers rely on the lexical order of these
 * strings, so never use {@link Instant#toString()} (it drops zero fractions and may print nanoseconds).
 *
 * <p>The clock is replaceable ({@link #useClock(Clock)}) so tests can pin "now"; it defaults to the system UTC clock.
 */
public final class Dates {

  /** {@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'} in UTC, the output of JavaScript's {@code Date.prototype.toISOString}. */
  public static final DateTimeFormatter ISO_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private static final Clock SYSTEM_CLOCK = Clock.systemUTC();
  private static volatile Clock clock = SYSTEM_CLOCK;

  private Dates() {}

  /**
   * @return the current time as {@code new Date().toISOString()} would print it
   */
  public static String nowIso() {
    return toIso(now());
  }

  /**
   * @return the current instant, from the configured clock
   */
  public static Instant now() {
    return clock.instant();
  }

  /**
   * @return milliseconds since the epoch ({@code Date.now()})
   */
  public static long nowEpochMillis() {
    return clock.millis();
  }

  /**
   * @return whole seconds since the epoch ({@code Math.floor(Date.now() / 1000)}), used for {@code expirationTime}
   */
  public static long nowEpochSeconds() {
    return Math.floorDiv(clock.millis(), 1000L);
  }

  /**
   * Formats an instant like {@code Date.prototype.toISOString} (truncated to milliseconds).
   *
   * @param instant the instant
   * @return its ISO string
   */
  public static String toIso(Instant instant) {
    return ISO_FORMATTER.format(Objects.requireNonNull(instant, "instant"));
  }

  /**
   * @param epochMillis milliseconds since the epoch
   * @return the ISO string of that moment
   */
  public static String toIso(long epochMillis) {
    return toIso(Instant.ofEpochMilli(epochMillis));
  }

  /**
   * Parses a date string leniently, like the JavaScript {@code Date} constructor does for the formats Booster
   * meets: ISO instants with any number of fraction digits, with {@code Z} or an offset; local date-times (taken as
   * UTC) and plain dates (UTC midnight, as JavaScript does for date-only strings).
   *
   * @param text the date text
   * @return the instant
   * @throws IllegalArgumentException when the text is not a supported date
   */
  public static Instant parse(String text) {
    Objects.requireNonNull(text, "text");
    String value = text.trim();
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ignored) {
      // try the next format
    }
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException ignored) {
      // try the next format
    }
    try {
      return ZonedDateTime.parse(value).toInstant();
    } catch (DateTimeParseException ignored) {
      // try the next format
    }
    try {
      return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException ignored) {
      // try the next format
    }
    try {
      return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid date: '" + text + "'", e);
    }
  }

  /**
   * {@code Date.parse(text)}.
   *
   * @param text a date string accepted by {@link #parse(String)}
   * @return milliseconds since the epoch
   */
  public static long toEpochMillis(String text) {
    return parse(text).toEpochMilli();
  }

  /**
   * Re-formats any accepted date string into the canonical Booster ISO format.
   *
   * @param text a date string accepted by {@link #parse(String)}
   * @return the canonical ISO string
   */
  public static String normalizeIso(String text) {
    return toIso(parse(text));
  }

  /**
   * @return the clock used by {@link #now()}
   */
  public static Clock clock() {
    return clock;
  }

  /**
   * Replaces the clock (for tests).
   *
   * @param newClock the clock to use
   */
  public static void useClock(Clock newClock) {
    clock = Objects.requireNonNull(newClock, "newClock");
  }

  /** Restores the system UTC clock. */
  public static void resetClock() {
    clock = SYSTEM_CLOCK;
  }
}
