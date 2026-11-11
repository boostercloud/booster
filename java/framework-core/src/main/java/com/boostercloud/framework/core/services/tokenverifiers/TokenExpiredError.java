package com.boostercloud.framework.core.services.tokenverifiers;

import java.time.Instant;

/**
 * Port of {@code jsonwebtoken}'s {@code TokenExpiredError} ({@code jwt expired}); {@code BoosterTokenVerifier} maps it
 * to {@code BoosterTokenExpiredError}.
 */
public class TokenExpiredError extends JsonWebTokenError {

  private final transient Instant expiredAt;

  /**
   * @param message the message ({@code jwt expired})
   * @param expiredAt the {@code exp} claim
   */
  public TokenExpiredError(String message, Instant expiredAt) {
    super(message);
    this.expiredAt = expiredAt;
  }

  /**
   * @return the {@code exp} claim
   */
  public Instant expiredAt() {
    return expiredAt;
  }
}
