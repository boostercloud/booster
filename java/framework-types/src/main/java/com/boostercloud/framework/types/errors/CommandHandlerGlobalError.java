package com.boostercloud.framework.types.errors;

import com.boostercloud.framework.types.concepts.CommandMetadata;
import com.boostercloud.framework.types.envelope.CommandEnvelope;

/**
 * Context of an error thrown by a command handler; hook {@code onCommandHandlerError(error, commandEnvelope, commandMetadata)}. Port of {@code CommandHandlerGlobalError}; the original error is the last constructor argument.
 */
public final class CommandHandlerGlobalError extends GlobalErrorContainer {
  private final CommandEnvelope commandEnvelope;
  private final CommandMetadata commandMetadata;

  /**
   * @param commandEnvelope the command
   * @param commandMetadata the command metadata
   * @param originalError the error that happened
   */
  public CommandHandlerGlobalError(CommandEnvelope commandEnvelope, CommandMetadata commandMetadata, Throwable originalError) {
    super(originalError);
    this.commandEnvelope = commandEnvelope;
    this.commandMetadata = commandMetadata;
  }

  /**
   * @return the command
   */
  public CommandEnvelope commandEnvelope() {
    return commandEnvelope;
  }

  /**
   * @return the command metadata
   */
  public CommandMetadata commandMetadata() {
    return commandMetadata;
  }
}
