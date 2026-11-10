package com.boostercloud.framework.provider.azure.infrastructure.process;

import com.boostercloud.framework.common.RunCommand;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An external command: what {@code runCommand(path, command, ignoreLogs)} receives in TypeScript, plus the environment
 * variables added to the inherited environment.
 *
 * @param workingDirectory the directory the command runs in
 * @param arguments the executable and its arguments (no shell)
 * @param environment variables added to (or replacing in) the current process environment
 * @param ignoreLogs whether the output is not echoed to the console
 */
public record Command(Path workingDirectory, List<String> arguments, Map<String, String> environment, boolean ignoreLogs) {

  /** Canonical constructor: copies the collections. */
  public Command {
    Objects.requireNonNull(workingDirectory, "workingDirectory");
    arguments = List.copyOf(arguments);
    if (arguments.isEmpty()) {
      throw new IllegalArgumentException("A command needs an executable");
    }
    environment = environment == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(environment));
  }

  /**
   * {@code runCommand(path, commandLine, ignoreLogs)}: the command line is split on single spaces, as TypeScript does.
   *
   * @param workingDirectory the directory
   * @param commandLine the command line
   * @param ignoreLogs whether the output is not echoed
   * @return the command
   */
  public static Command of(Path workingDirectory, String commandLine, boolean ignoreLogs) {
    return new Command(workingDirectory, RunCommand.splitCommand(commandLine), Map.of(), ignoreLogs);
  }

  /**
   * @return the arguments joined with single spaces
   */
  public String commandLine() {
    return String.join(" ", arguments);
  }
}
