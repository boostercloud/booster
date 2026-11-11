package com.boostercloud.framework.types.injectable;

import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;
import java.util.Objects;

/**
 * A command contributed by the application to the {@code boost} CLI. Replaces an {@code @effect/cli} command of the
 * TypeScript {@code Injectable} feature.
 */
public interface InjectableCommand {
  /**
   * @return the sub-command name ({@code boost <name> ...})
   */
  String name();

  /**
   * @return a one-line description for the help output
   */
  default String description() {
    return "";
  }

  /**
   * Runs the command.
   *
   * @param config the application configuration (TypeScript provides it through {@code BoosterConfigTag})
   * @param arguments the arguments after the sub-command name
   */
  void run(BoosterConfig config, List<String> arguments);

  /**
   * @param name the sub-command name
   * @param handler what to run
   * @return a command
   */
  static InjectableCommand of(String name, Handler handler) {
    return of(name, "", handler);
  }

  /**
   * @param name the sub-command name
   * @param description a one-line description
   * @param handler what to run
   * @return a command
   */
  static InjectableCommand of(String name, String description, Handler handler) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(handler, "handler");
    return new InjectableCommand() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return description;
      }

      @Override
      public void run(BoosterConfig config, List<String> arguments) {
        handler.run(config, arguments);
      }

      @Override
      public String toString() {
        return "InjectableCommand{" + name + "}";
      }
    };
  }

  /** The body of a command. */
  @FunctionalInterface
  interface Handler {
    /**
     * @param config the application configuration
     * @param arguments the arguments after the sub-command name
     */
    void run(BoosterConfig config, List<String> arguments);
  }
}
