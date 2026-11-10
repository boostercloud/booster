package com.boostercloud.framework.types.errors;

import java.util.Map;

/**
 * A conditional write failed because the stored version changed (HTTP 500). Thrown by providers
 * from {@code readModels.store} when {@code expectedCurrentVersion} does not match; core retries on it. Port of the TypeScript class of the same name.
 */
public class OptimisticConcurrencyUnexpectedVersionError extends BoosterError {
  /**
   * @param message the error message
   */
  public OptimisticConcurrencyUnexpectedVersionError(String message) {
    super(message);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   */
  public OptimisticConcurrencyUnexpectedVersionError(String message, String code) {
    super(message, code);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   * @param data extra data, or {@code null}
   */
  public OptimisticConcurrencyUnexpectedVersionError(String message, String code, Map<String, Object> data) {
    super(message, code, data);
  }

  /**
   * @param message the error message
   * @param cause the underlying cause
   */
  public OptimisticConcurrencyUnexpectedVersionError(String message, Throwable cause) {
    super(message, cause);
  }
}
