package com.boostercloud.framework.types.errors;

import java.util.Map;

/**
 * Something does not exist (HTTP 404). Port of the TypeScript class of the same name.
 */
public class NotFoundError extends BoosterError {
  /**
   * @param message the error message
   */
  public NotFoundError(String message) {
    super(message);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   */
  public NotFoundError(String message, String code) {
    super(message, code);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   * @param data extra data, or {@code null}
   */
  public NotFoundError(String message, String code, Map<String, Object> data) {
    super(message, code, data);
  }

  /**
   * @param message the error message
   * @param cause the underlying cause
   */
  public NotFoundError(String message, Throwable cause) {
    super(message, cause);
  }
}
