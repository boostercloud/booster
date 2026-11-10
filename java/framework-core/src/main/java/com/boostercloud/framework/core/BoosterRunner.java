package com.boostercloud.framework.core;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.InternalInfo;
import com.boostercloud.framework.types.errors.BoosterErrors;
import com.boostercloud.framework.types.injectable.Injectable;
import com.boostercloud.framework.types.injectable.InjectableCommand;
import com.boostercloud.framework.types.provider.ProviderInfrastructure;
import com.boostercloud.framework.types.provider.ProviderInfrastructure.Operation;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

/**
 * {@link Booster#run(Class, String[])}: the command-line contract between the {@code boost} CLI and a Booster
 * application ({@code java/cli/README.md}).
 *
 * <ul>
 *   <li>{@code start [--port <N>]}: starts the local server through {@code provider.infrastructure().start(config, N)}
 *       (default port {@code BOOSTER_INTERNAL_LOCAL_PORT}, else 3000; the port is also published with
 *       {@link InternalInfo#setBoosterLocalPort(int)}); blocks while the server runs.</li>
 *   <li>{@code synth}, {@code deploy}, {@code nuke}: the infrastructure operation.</li>
 *   <li>With {@code BOOSTER_CLI_HOOK=true}: {@code <command> <args...>} or {@code --help} runs the commands of
 *       {@code config.injectable} (TypeScript runs them from {@code Booster.start}); without injectable commands
 *       nothing happens.</li>
 * </ul>
 *
 * <p>Exit codes: 0 on success, 1 when the start-up or the action fails (also when the provider does not support the
 * operation), 2 for a usage error.
 */
final class BoosterRunner {

  /** The usage text. */
  static final String USAGE = "Usage: <main class> start [--port <port>] | synth | deploy | nuke";

  /**
   * What {@link Booster#run(Class, String[])} does after running.
   *
   * @param exitCode the exit code
   * @param exitJvm whether the JVM must be exited with it
   */
  record Outcome(int exitCode, boolean exitJvm) {}

  private BoosterRunner() {}

  static Outcome run(Class<?> mainClass, String[] args, PrintStream out, PrintStream err) {
    List<String> arguments = args == null ? List.of() : Arrays.asList(args);
    try {
      Booster.start(mainClass);
    } catch (RuntimeException e) {
      printError(err, "Booster could not start the application", e);
      return new Outcome(1, true);
    }
    BoosterConfig config = Booster.config();
    if ("true".equals(EnvironmentVariables.get(EnvironmentVariables.BOOSTER_CLI_HOOK).map(String::trim).orElse(null))) {
      return new Outcome(runInjectable(mainClass, config, arguments, out, err), true);
    }
    if (arguments.isEmpty()) {
      err.println(USAGE);
      return new Outcome(2, true);
    }
    String action = arguments.getFirst();
    List<String> rest = arguments.subList(1, arguments.size());
    return switch (action) {
      case "start" -> start(config, rest, err);
      case "synth" -> new Outcome(infrastructureOperation(config, Operation.SYNTH, rest, err), true);
      case "deploy" -> new Outcome(infrastructureOperation(config, Operation.DEPLOY, rest, err), true);
      case "nuke" -> new Outcome(infrastructureOperation(config, Operation.NUKE, rest, err), true);
      default -> {
        err.println("Unknown action '" + action + "'. " + USAGE);
        yield new Outcome(2, true);
      }
    };
  }

  private static Outcome start(BoosterConfig config, List<String> arguments, PrintStream err) {
    Integer port = null;
    for (int i = 0; i < arguments.size(); i++) {
      String argument = arguments.get(i);
      if ((argument.equals("--port") || argument.equals("-p")) && i + 1 < arguments.size()) {
        port = parsePort(arguments.get(++i));
      } else if (argument.startsWith("--port=")) {
        port = parsePort(argument.substring("--port=".length()));
      } else {
        port = -1;
      }
      if (port == null || port < 0) {
        err.println("Invalid arguments " + arguments + ". " + USAGE);
        return new Outcome(2, true);
      }
    }
    try {
      int effectivePort;
      if (port == null) {
        effectivePort = Integer.parseInt(InternalInfo.boosterLocalPort());
      } else {
        effectivePort = port;
        InternalInfo.setBoosterLocalPort(port);
      }
      ProviderInfrastructure infrastructure = infrastructure(config, Operation.START, err);
      if (infrastructure == null) {
        return new Outcome(1, true);
      }
      infrastructure.start(config, effectivePort);
      return new Outcome(0, false);
    } catch (RuntimeException e) {
      printError(err, "The 'start' operation failed", e);
      return new Outcome(1, true);
    }
  }

  private static int infrastructureOperation(BoosterConfig config, Operation operation, List<String> arguments,
      PrintStream err) {
    if (!arguments.isEmpty()) {
      err.println("Unexpected arguments " + arguments + " for '" + operation.methodName() + "'. " + USAGE);
      return 2;
    }
    try {
      ProviderInfrastructure infrastructure = infrastructure(config, operation, err);
      if (infrastructure == null) {
        return 1;
      }
      switch (operation) {
        case SYNTH -> infrastructure.synth(config);
        case DEPLOY -> infrastructure.deploy(config);
        case NUKE -> infrastructure.nuke(config);
        case START -> throw new IllegalArgumentException("start is not a one-shot operation");
      }
      return 0;
    } catch (RuntimeException e) {
      printError(err, "The '" + operation.methodName() + "' operation failed", e);
      return 1;
    }
  }

  private static ProviderInfrastructure infrastructure(BoosterConfig config, Operation operation, PrintStream err) {
    ProviderInfrastructure infrastructure = config.provider().infrastructure();
    if (infrastructure == null || !infrastructure.supports(operation)) {
      err.println("Attempted to perform the '" + operation.methodName() + "' operation with a provider that does not"
          + " support this feature, please check your environment configuration.");
      return null;
    }
    return infrastructure;
  }

  private static Integer parsePort(String value) {
    try {
      int port = Integer.parseInt(value);
      return port >= 0 && port <= 65535 ? port : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static int runInjectable(Class<?> mainClass, BoosterConfig config, List<String> arguments, PrintStream out,
      PrintStream err) {
    Injectable injectable = config.injectable;
    if (injectable == null) {
      return 0;
    }
    if (arguments.isEmpty() || arguments.getFirst().equals("--help") || arguments.getFirst().equals("-h")) {
      printInjectableHelp(mainClass, injectable, out);
      return 0;
    }
    String name = arguments.getFirst();
    InjectableCommand command = injectable.command(name).orElse(null);
    if (command == null) {
      err.println("Invalid subcommand for boost - use one of '"
          + String.join("', '", injectable.commands().stream().map(InjectableCommand::name).toList()) + "'");
      return 1;
    }
    try {
      command.run(config, List.copyOf(arguments.subList(1, arguments.size())));
      return 0;
    } catch (RuntimeException e) {
      printError(err, "The command '" + name + "' failed", e);
      return 1;
    }
  }

  private static void printInjectableHelp(Class<?> mainClass, Injectable injectable, PrintStream out) {
    String version = mainClass.getPackage() == null || mainClass.getPackage().getImplementationVersion() == null
        ? "unknown"
        : mainClass.getPackage().getImplementationVersion();
    out.println("boost " + version);
    out.println();
    out.println("USAGE");
    out.println();
    out.println("$ boost <command> [args...]");
    out.println();
    out.println("COMMANDS");
    out.println();
    for (InjectableCommand command : injectable.commands()) {
      out.println("  - " + command.name() + (command.description().isEmpty() ? "" : "  " + command.description()));
    }
  }

  private static void printError(PrintStream err, String context, Throwable error) {
    err.println(context + ": " + BoosterErrors.errorToString(error));
    error.printStackTrace(err);
  }
}
