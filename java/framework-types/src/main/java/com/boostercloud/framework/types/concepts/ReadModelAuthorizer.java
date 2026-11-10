package com.boostercloud.framework.types.concepts;

import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;

/**
 * Port of {@code ReadModelAuthorizer}: returns normally to allow the read, throws to deny it.
 */
@FunctionalInterface
public interface ReadModelAuthorizer {
  /**
   * @param currentUser the user, {@code null} for anonymous requests
   * @param readModelRequestEnvelope the read model request
   */
  void authorize(UserEnvelope currentUser, ReadModelRequestEnvelope readModelRequestEnvelope);
}
