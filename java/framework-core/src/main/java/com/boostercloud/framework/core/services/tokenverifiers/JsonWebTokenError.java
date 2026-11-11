package com.boostercloud.framework.core.services.tokenverifiers;

/**
 * Port of {@code jsonwebtoken}'s {@code JsonWebTokenError}: a token that cannot be verified (malformed, bad signature,
 * wrong issuer, ...). Its JavaScript text is {@code "JsonWebTokenError: <message>"}.
 */
public class JsonWebTokenError extends RuntimeException {

  /**
   * @param message the {@code jsonwebtoken} message, e.g. {@code invalid signature}
   */
  public JsonWebTokenError(String message) {
    super(message);
  }

  /**
   * @param message the message
   * @param cause the cause
   */
  public JsonWebTokenError(String message, Throwable cause) {
    super(message, cause);
  }
}
