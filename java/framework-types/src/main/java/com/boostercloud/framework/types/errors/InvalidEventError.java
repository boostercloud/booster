package com.boostercloud.framework.types.errors;

import java.util.Map;

/**
 * An event is not registered (HTTP 500). Port of the TypeScript class of the same name.
 */
public class InvalidEventError extends BoosterError {
  /**
   * @param message the error message
   */
  public InvalidEventError(String message) {
    super(message);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   */
  public InvalidEventError(String message, String code) {
    super(message, code);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   * @param data extra data, or {@code null}
   */
  public InvalidEventError(String message, String code, Map<String, Object> data) {
    super(message, code, data);
  }

  /**
   * @param message the error message
   * @param cause the underlying cause
   */
  public InvalidEventError(String message, Throwable cause) {
    super(message, cause);
  }
}
