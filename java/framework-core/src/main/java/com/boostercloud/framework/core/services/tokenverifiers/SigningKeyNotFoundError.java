package com.boostercloud.framework.core.services.tokenverifiers;

/**
 * Port of {@code jwks-rsa}'s {@code SigningKeyNotFoundError}: {@code Unable to find a signing key that matches
 * '<kid>'}.
 */
public class SigningKeyNotFoundError extends RuntimeException {

  /**
   * @param message the message
   */
  public SigningKeyNotFoundError(String message) {
    super(message);
  }
}
