package com.boostercloud.framework.types.errors;

import java.util.Map;

/**
 * The request uses an unsupported protocol feature (HTTP 400). Port of the TypeScript class of the same name.
 */
public class InvalidProtocolError extends BoosterError {
  /**
   * @param message the error message
   */
  public InvalidProtocolError(String message) {
    super(message);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   */
  public InvalidProtocolError(String message, String code) {
    super(message, code);
  }

  /**
   * @param message the error message
   * @param code the error code ({@code null} for the class name)
   * @param data extra data, or {@code null}
   */
  public InvalidProtocolError(String message, String code, Map<String, Object> data) {
    super(message, code, data);
  }

  /**
   * @param message the error message
   * @param cause the underlying cause
   */
  public InvalidProtocolError(String message, Throwable cause) {
    super(message, cause);
  }
}
