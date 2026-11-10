package com.boostercloud.cli.hooks.commandnotfound;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.common.Errors;
import com.boostercloud.cli.services.Environment;
import com.boostercloud.cli.services.ProviderService;
import com.boostercloud.cli.services.process.RunningProcess;
import com.boostercloud.cli.services.project.ConfiguredEnvironments;
import com.boostercloud.cli.services.project.ProjectConfig;
import com.boostercloud.cli.services.project.ProjectLayout;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * What happens when the first argument is not a CLI command ({@code hooks/command_not_found/custom-parse.ts}): the
 * command is handed to the user's application, so that it can run its own injected commands (rockets).
 *
 * <p>{@code BOOSTER_CLI_HOOK=true} is set, the first {@code -e <environment>} pair selects the environment and is
 * removed from the arguments, and the already compiled application (no build is triggered) is launched with
 * {@code <command> <remaining args>} ({@code --help} when there is nothing else). The application's exit code is
 * the CLI's exit code.
 */
public final class CustomParse {
  /** The variable that tells the application it was launched by this hook. */
  public static final String BOOSTER_CLI_HOOK = "BOOSTER_CLI_HOOK";

  /**
   * The split command line.
   *
   * @param id the command (empty when the command line starts with {@code -e})
   * @param environment the value after the first {@code -e}, or {@code null}
   * @param argv the remaining arguments
   */
  public record HookArguments(String id, String environment, List<String> argv) {}

  private CustomParse() {}

  /**
   * Splits the command line: a leading {@code -e} means there is no command, and the first {@code -e} and the
   * token after it (when there is one) are extracted.
   *
   * @param id the first argument
   * @param argv the other arguments
   * @return the split arguments
   */
  public static HookArguments parse(String id, List<String> argv) {
    String commandId = id;
    List<String> rest = new ArrayList<>(argv);
    if ("-e".equals(commandId)) {
      commandId = "";
      rest.addFirst("-e");
    }
    String environment = null;
    int eIndex = rest.indexOf("-e");
    if (eIndex != -1 && eIndex + 1 < rest.size()) {
      environment = rest.get(eIndex + 1);
      rest.remove(eIndex + 1);
      rest.remove(eIndex);
    }
    return new HookArguments(commandId, environment, List.copyOf(rest));
  }

  /**
   * Runs the hook.
   *
   * @param ctx the CLI context
   * @param id the first argument
   * @param argv the other arguments
   * @return the exit code
   */
  public static int hook(CliContext ctx, String id, List<String> argv) {
    ctx.environment().put(BOOSTER_CLI_HOOK, "true");
    HookArguments arguments = parse(id, argv);
    if (!Environment.initializeEnvironment(ctx, ctx.logger(), arguments.environment())) {
      return 0;
    }
    List<String> args = arguments.argv();
    if (arguments.id().isEmpty() && args.isEmpty()) {
      args = List.of("--help");
    }
    try {
      ProjectLayout layout;
      try {
        layout = ProjectLayout.of(ctx.cwd());
      } catch (CliException e) {
        throw new CliException(
            "command " + arguments.id() + " not found. Run 'boost --help' to see the available commands", e);
      }
      if (!Files.isDirectory(layout.classesDirectory())) {
        throw new CliException(
            "The application has not been compiled yet (" + layout.classesDirectory() + " does not exist). Run 'boost"
                + " build' first");
      }
      ProjectConfig config =
          new ProjectConfig(
              layout.projectDir(),
              Environment.currentEnvironment(ctx),
              layout,
              new ConfiguredEnvironments(java.util.Set.of(), true, java.util.Map.of()));
      RunningProcess process =
          ctx.process().spawn(ProviderService.applicationCommand(ctx, config, arguments.id(), args), layout.projectDir());
      return process.waitFor();
    } catch (CliException e) {
      Errors.printError(ctx.err(), Errors.messageOf(e));
      return 1;
    }
  }
}
