package com.boostercloud.framework.core.services.tokenverifiers;

import java.time.Instant;

/**
 * Port of {@code jsonwebtoken}'s {@code NotBeforeError} ({@code jwt not active}); {@code BoosterTokenVerifier} maps
 * it to {@code BoosterTokenNotBeforeError}.
 */
public class NotBeforeError extends JsonWebTokenError {

  private final transient Instant date;

  /**
   * @param message the message ({@code jwt not active})
   * @param date the {@code nbf} claim
   */
  public NotBeforeError(String message, Instant date) {
    super(message);
    this.date = date;
  }

  /**
   * @return the {@code nbf} claim
   */
  public Instant date() {
    return date;
  }
}
