package com.boostercloud.framework.types.errors;

import java.util.Map;

/**
 * The JWT is not active yet (HTTP 401). Port of the TypeScript class of the same name.
 */
public class BoosterTokenNotBeforeError extends BoosterError {
  /**
   * @param message the error message
   */
  public BoosterTokenNotBeforeError(String message) {
    super(message);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   */
  public BoosterTokenNotBeforeError(String message, String code) {
    super(message, code);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   * @param data extra data, or {@code null}
   */
  public BoosterTokenNotBeforeError(String message, String code, Map<String, Object> data) {
    super(message, code, data);
  }

  /**
   * @param message the error message
   * @param cause the underlying cause
   */
  public BoosterTokenNotBeforeError(String message, Throwable cause) {
    super(message, cause);
  }
}
