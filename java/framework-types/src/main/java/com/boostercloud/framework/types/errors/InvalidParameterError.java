package com.boostercloud.framework.types.errors;

import java.util.Map;

/**
 * A request parameter is invalid (HTTP 400). Port of the TypeScript class of the same name.
 */
public class InvalidParameterError extends BoosterError {
  /**
   * @param message the error message
   */
  public InvalidParameterError(String message) {
    super(message);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   */
  public InvalidParameterError(String message, String code) {
    super(message, code);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   * @param data extra data, or {@code null}
   */
  public InvalidParameterError(String message, String code, Map<String, Object> data) {
    super(message, code, data);
  }

  /**
   * @param message the error message
   * @param cause the underlying cause
   */
  public InvalidParameterError(String message, Throwable cause) {
    super(message, cause);
  }
}
