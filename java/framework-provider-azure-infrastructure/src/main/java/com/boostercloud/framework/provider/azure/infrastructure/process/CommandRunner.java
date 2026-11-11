package com.boostercloud.framework.provider.azure.infrastructure.process;

import com.boostercloud.framework.common.RunCommandError;

/**
 * Runs external commands ({@code terraform}, {@code az}). The production implementation is
 * {@link ProcessCommandRunner}; tests pass a recorder so they can assert the exact command sequence without running
 * anything.
 */
@FunctionalInterface
public interface CommandRunner {

  /**
   * Runs a command to completion ({@code runCommand} of {@code framework-common-helpers}).
   *
   * @param command the command
   * @return the standard output
   * @throws RunCommandError when the exit code is not 0 (its message is the standard error, the value TypeScript
   *     rejects with)
   */
  String run(Command command);
}
