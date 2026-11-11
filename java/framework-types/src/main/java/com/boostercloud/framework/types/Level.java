package com.boostercloud.framework.types;

/**
 * Log levels, ordered by severity. Port of the TypeScript numeric enum {@code Level} ({@code debug = 0},
 * {@code info = 1}, {@code warn = 2}, {@code error = 3}); {@link #value()} keeps the numbers because the logger
 * compares them with {@code <=}.
 */
public enum Level {
  /** {@code 0}. */
  DEBUG(0),
  /** {@code 1}. */
  INFO(1),
  /** {@code 2}. */
  WARN(2),
  /** {@code 3}. */
  ERROR(3);

  private final int value;

  Level(int value) {
    this.value = value;
  }

  /**
   * @return the TypeScript numeric value
   */
  public int value() {
    return value;
  }

  /**
   * TypeScript's {@code config.logLevel <= level}: whether a message of {@code level} is emitted when this is the
   * configured threshold.
   *
   * @param level the level of a message
   * @return true when {@code this.value() <= level.value()}
   */
  public boolean enables(Level level) {
    return value <= level.value;
  }

  /**
   * Reverse mapping, like TypeScript's {@code Level[1] === 'info'}.
   *
   * @param value a numeric level
   * @return the level
   * @throws IllegalArgumentException for values other than 0..3
   */
  public static Level fromValue(int value) {
    for (Level level : values()) {
      if (level.value == value) {
        return level;
      }
    }
    throw new IllegalArgumentException("Unknown log level " + value);
  }
}
