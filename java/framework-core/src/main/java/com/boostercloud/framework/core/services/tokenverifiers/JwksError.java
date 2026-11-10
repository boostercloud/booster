package com.boostercloud.framework.core.services.tokenverifiers;

/**
 * Port of {@code jwks-rsa}'s {@code JwksError}: the JWKS document could not be fetched or has no usable keys.
 */
public class JwksError extends RuntimeException {

  /**
   * @param message the message
   */
  public JwksError(String message) {
    super(message);
  }

  /**
   * @param message the message
   * @param cause the cause
   */
  public JwksError(String message, Throwable cause) {
    super(message, cause);
  }
}
