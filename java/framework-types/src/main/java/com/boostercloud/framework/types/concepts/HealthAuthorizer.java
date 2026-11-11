package com.boostercloud.framework.types.concepts;

import com.boostercloud.framework.types.envelope.HealthEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;

/**
 * Port of {@code HealthAuthorizer}: returns normally to allow access to the health endpoint, throws to deny it.
 */
@FunctionalInterface
public interface HealthAuthorizer {
  /**
   * @param currentUser the user, {@code null} for anonymous requests
   * @param healthEnvelope the health request envelope
   */
  void authorize(UserEnvelope currentUser, HealthEnvelope healthEnvelope);
}
