package com.boostercloud.framework.types.injectable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * CLI commands contributed by the application ({@code config.injectable}). Java replacement of TypeScript's
 * Effect-based {@code Injectable} ({@code { commands, runMain?, contextProvider? }}): when the environment variable
 * {@code BOOSTER_CLI_HOOK} trimmed equals {@code true}, {@code Booster.start} dispatches the process arguments to
 * these commands instead of starting normally. {@code runMain} and {@code contextProvider} (Effect runtime overrides)
 * have no Java counterpart.
 *
 * @param commands the commands, at least one
 */
public record Injectable(List<InjectableCommand> commands) {

  /** Canonical constructor; requires a non-empty list (TypeScript: a non-empty tuple). */
  public Injectable {
    Objects.requireNonNull(commands, "commands");
    if (commands.isEmpty()) {
      throw new IllegalArgumentException("An Injectable needs at least one command");
    }
    commands = List.copyOf(commands);
  }

  /**
   * @param commands the commands
   * @return the injectable
   */
  public static Injectable of(InjectableCommand... commands) {
    return new Injectable(List.of(commands));
  }

  /**
   * @param name a sub-command name
   * @return the command with that name
   */
  public Optional<InjectableCommand> command(String name) {
    return commands.stream().filter(command -> command.name().equals(name)).findFirst();
  }
}
