package com.boostercloud.framework.provider.local.store;

/**
 * An error raised by the embedded store. Port of NeDB's errors, which are plain {@code Error}s that sometimes carry
 * extra properties; the TypeScript provider exposes that shape as {@code NedbError = Error & { [key]: unknown }} and
 * checks {@code error.errorType == 'uniqueViolated'}.
 *
 * <p>{@link #errorType()} is {@code "uniqueViolated"} for unique-index violations (with the offending {@link #key()}),
 * and {@code null} for every other error (invalid queries, invalid field names, I/O failures, ...).
 */
public class NedbError extends RuntimeException {

  /** {@code errorType} of a unique-index violation. */
  public static final String UNIQUE_VIOLATED = "uniqueViolated";

  private final transient String errorType;
  private final transient Object key;

  /**
   * @param message the message (NeDB's text)
   */
  public NedbError(String message) {
    this(message, null, null, null);
  }

  /**
   * @param message the message
   * @param cause the cause
   */
  public NedbError(String message, Throwable cause) {
    this(message, null, null, cause);
  }

  /**
   * @param message the message
   * @param errorType the error type, or {@code null}
   * @param key the offending key, or {@code null}
   */
  public NedbError(String message, String errorType, Object key) {
    this(message, errorType, key, null);
  }

  /**
   * @param message the message
   * @param errorType the error type, or {@code null}
   * @param key the offending key, or {@code null}
   * @param cause the cause, or {@code null}
   */
  public NedbError(String message, String errorType, Object key, Throwable cause) {
    super(message, cause);
    this.errorType = errorType;
    this.key = key;
  }

  /**
   * @return {@code "uniqueViolated"} for unique-index violations, otherwise {@code null}
   */
  public String errorType() {
    return errorType;
  }

  /**
   * @return the key that violated a unique index, or {@code null}
   */
  public Object key() {
    return key;
  }

  /**
   * The error NeDB's binary search tree throws on a unique-key violation:
   * {@code Can't insert key <JSON.stringify(key)>, it violates the unique constraint}.
   *
   * @param key the duplicated key
   * @return the error
   */
  public static NedbError uniqueViolated(Object key) {
    return new NedbError("Can't insert key " + Js.stringify(key) + ", it violates the unique constraint",
        UNIQUE_VIOLATED, key);
  }
}
