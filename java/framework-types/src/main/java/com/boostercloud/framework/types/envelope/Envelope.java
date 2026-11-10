package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;

/**
 * Common fields of every request envelope. Port of {@code Envelope}.
 */
public interface Envelope {
  /**
   * @return id of the request
   */
  UUID requestID();

  /**
   * @return the user, or {@code null} for anonymous requests
   */
  UserEnvelope currentUser();

  /**
   * @return the request context, or {@code null}
   */
  ContextEnvelope context();
}
