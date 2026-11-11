package com.boostercloud.framework.core.internal;

import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.envelope.UserEnvelope;

/**
 * Expiry checks for the identities kept by websocket connections and subscriptions. TypeScript relied on the provider
 * to remove expired connection data and subscriptions (on AWS the API Gateway closes sockets after two hours and
 * DynamoDB's TTL deletes the records); the local and Azure providers keep them, so core checks the times itself.
 *
 * <p>Internal to framework-core: not part of the user-facing API.
 */
public final class Expirations {

  private Expirations() {}

  /**
   * @param expirationTime an expiration time in epoch seconds ({@code ConnectionDataEnvelope},
   *     {@code SubscriptionEnvelope})
   * @return whether it has been reached
   */
  public static boolean hasPassed(long expirationTime) {
    return expirationTime <= Dates.nowEpochSeconds();
  }

  /**
   * Whether the token a user was authenticated with has expired: its {@code exp} claim (epoch seconds) has been
   * reached, as {@code jsonwebtoken} decides it. A user without {@code exp} (or without user) never expires.
   *
   * @param user the user stored with a connection or subscription, or {@code null}
   * @return whether the token has expired
   */
  public static boolean tokenHasExpired(UserEnvelope user) {
    return user != null && user.claims().get("exp") instanceof Number exp
        && exp.doubleValue() <= Dates.nowEpochSeconds();
  }
}
