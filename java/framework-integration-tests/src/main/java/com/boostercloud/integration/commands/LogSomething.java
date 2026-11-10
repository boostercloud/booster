package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.CommandAuthorizer;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotAuthorizedError;

/**
 * A command with a custom authorization policy ({@code src/commands/log-something.ts}): only users whose
 * {@code canLogSomething} claim is the boolean {@code true} may run it.
 */
@Command(authorize = LogSomething.CanLogSomething.class)
public record LogSomething() {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(LogSomething command, Register register) {
    System.out.println("something");
  }

  /** Rejects with {@code You are not allowed to log something} unless {@code claims.canLogSomething === true}. */
  public static final class CanLogSomething implements CommandAuthorizer {
    @Override
    public void authorize(UserEnvelope currentUser, CommandEnvelope commandEnvelope) {
      if (currentUser == null || !Boolean.TRUE.equals(currentUser.claims().get("canLogSomething"))) {
        throw new NotAuthorizedError("You are not allowed to log something");
      }
    }
  }
}
