package com.boostercloud.framework.types.concepts;

import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;

/**
 * Port of {@code CommandAuthorizer}: returns normally to allow the command and throws (typically
 * {@code NotAuthorizedError}) to deny it; the exception reaches the client. Both arguments may be {@code null}.
 */
@FunctionalInterface
public interface CommandAuthorizer {
  /**
   * @param currentUser the user, {@code null} for anonymous requests
   * @param commandEnvelope the command envelope
   */
  void authorize(UserEnvelope currentUser, CommandEnvelope commandEnvelope);
}
