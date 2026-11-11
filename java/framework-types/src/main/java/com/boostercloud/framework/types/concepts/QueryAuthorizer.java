package com.boostercloud.framework.types.concepts;

import com.boostercloud.framework.types.envelope.QueryEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;

/**
 * Port of {@code QueryAuthorizer}: returns normally to allow the query, throws to deny it.
 */
@FunctionalInterface
public interface QueryAuthorizer {
  /**
   * @param currentUser the user, {@code null} for anonymous requests
   * @param queryEnvelope the query envelope
   */
  void authorize(UserEnvelope currentUser, QueryEnvelope queryEnvelope);
}
