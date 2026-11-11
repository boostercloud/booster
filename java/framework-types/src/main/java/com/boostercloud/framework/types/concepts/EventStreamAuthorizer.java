package com.boostercloud.framework.types.concepts;

import com.boostercloud.framework.types.envelope.EventSearchRequest;
import com.boostercloud.framework.types.envelope.UserEnvelope;

/**
 * Port of {@code EventStreamAuthorizer}: returns normally to allow reading an entity's events, throws to deny it.
 */
@FunctionalInterface
public interface EventStreamAuthorizer {
  /**
   * @param currentUser the user, {@code null} for anonymous requests
   * @param eventSearchRequest the event search request
   */
  void authorize(UserEnvelope currentUser, EventSearchRequest eventSearchRequest);
}
