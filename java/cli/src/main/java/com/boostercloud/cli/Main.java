package com.boostercloud.cli;

import com.boostercloud.cli.commands.Build;
import com.boostercloud.cli.commands.Clean;
import com.boostercloud.cli.commands.Deploy;
import com.boostercloud.cli.commands.Nuke;
import com.boostercloud.cli.commands.Start;
import com.boostercloud.cli.commands.Synth;
import com.boostercloud.cli.commands.add.Projection;
import com.boostercloud.cli.commands.add.Reducer;
import com.boostercloud.cli.commands.newcmd.Command;
import com.boostercloud.cli.commands.newcmd.Entity;
import com.boostercloud.cli.commands.newcmd.Event;
import com.boostercloud.cli.commands.newcmd.EventHandler;
import com.boostercloud.cli.commands.newcmd.Project;
import com.boostercloud.cli.commands.newcmd.Query;
import com.boostercloud.cli.commands.newcmd.ReadModel;
import com.boostercloud.cli.commands.newcmd.ScheduledCommand;
import com.boostercloud.cli.commands.newcmd.Type;
import com.boostercloud.cli.commands.stub.Publish;
import com.boostercloud.cli.common.ColorSupport;
import com.boostercloud.cli.common.Errors;
import com.boostercloud.cli.help.HelpRenderer;
import com.boostercloud.cli.help.ParseErrors;
import com.boostercloud.cli.hooks.commandnotfound.CustomParse;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * The {@code boost} executable ({@code bin/run} and oclif's {@code run()} in TypeScript).
 *
 * <p>Routing, as oclif does it: no arguments or {@code --help} show the root help, {@code --version} prints the
 * version, {@code help [command]} and {@code <command> --help} show a command's help, a topic ({@code new},
 * {@code add}, {@code stub}) lists its commands, a known command is parsed and executed, and anything else goes to
 * the {@code command_not_found} hook ({@link CustomParse}).
 */
public final class Main {
  /** Every command of the CLI. */
  public static final List<Class<?>> COMMANDS =
      List.of(
          Projection.class,
          Reducer.class,
          Build.class,
          Clean.class,
          Deploy.class,
          Command.class,
          Entity.class,
          EventHandler.class,
          Event.class,
          Project.class,
          Query.class,
          ReadModel.class,
          ScheduledCommand.class,
          Type.class,
          Nuke.class,
          Start.class,
          Publish.class,
          Synth.class);

  /** Exit code of command-line parsing errors (oclif's). */
  public static final int PARSE_ERROR_EXIT_CODE = 2;

  private Main() {}

  /**
   * Runs the CLI and exits with its exit code.
   *
   * @param args the command line
   */
  public static void main(String[] args) {
    ColorSupport.setLevel(ColorSupport.detect(System.getenv(), ColorSupport.isTerminal(), System.getProperty("os.name", "")));
    int exitCode = run(CliContext.live(), args);
    System.out.flush();
    System.err.flush();
    System.exit(exitCode);
  }

  /**
   * Runs the CLI.
   *
   * @param ctx the CLI context
   * @param args the command line
   * @return the exit code
   */
  public static int run(CliContext ctx, String... args) {
    try {
      Map<String, Class<?>> commands = commandsById();
      if (args.length == 0) {
        ctx.out().print(HelpRenderer.root(specs(ctx)));
        return 0;
      }
      String first = args[0];
      List<String> rest = Arrays.asList(args).subList(1, args.length);
      if (first.equals("--version")) {
        ctx.out().println(CliVersion.versionLine());
        return 0;
      }
      if (first.equals("--help") || first.equals("-h") || first.equals("help")) {
        return help(ctx, rest.isEmpty() ? null : rest.getFirst());
      }
      if (commands.containsKey(first)) {
        if (wantsHelp(rest)) {
          return help(ctx, first);
        }
        CommandLine commandLine = commandLine(ctx, commands.get(first));
        return commandLine.execute(rest.toArray(String[]::new));
      }
      if (topics().contains(first) && rest.isEmpty()) {
        ctx.out().print(HelpRenderer.topic(first, specs(ctx)));
        return 0;
      }
      return CustomParse.hook(ctx, first, rest);
    } catch (RuntimeException e) {
      Errors.printError(ctx.err(), Errors.messageOf(e));
      return 1;
    } finally {
      ctx.out().flush();
      ctx.err().flush();
    }
  }

  /**
   * Creates the picocli command line of a command, wired to the context: parsing errors are printed in oclif's
   * format and finish with exit code {@value #PARSE_ERROR_EXIT_CODE}.
   *
   * @param ctx the CLI context
   * @param commandClass the command
   * @return the command line
   */
  public static CommandLine commandLine(CliContext ctx, Class<?> commandClass) {
    CommandLine.IFactory factory = factory(ctx);
    CommandLine commandLine;
    try {
      commandLine = new CommandLine(factory.create(commandClass), factory);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot create the command " + commandClass.getName(), e);
    }
    commandLine.setExpandAtFiles(false);
    commandLine.setOut(new PrintWriter(ctx.out(), true));
    commandLine.setErr(new PrintWriter(ctx.err(), true));
    commandLine.setParameterExceptionHandler(
        (ex, arguments) -> {
          Errors.printParseError(ctx.err(), ParseErrors.message(ex));
          return PARSE_ERROR_EXIT_CODE;
        });
    commandLine.setExecutionExceptionHandler(
        (ex, cl, parseResult) -> {
          Errors.printError(ctx.err(), Errors.messageOf(ex));
          return 1;
        });
    return commandLine;
  }

  private static int help(CliContext ctx, String target) {
    if (target == null) {
      ctx.out().print(HelpRenderer.root(specs(ctx)));
      return 0;
    }
    Class<?> command = commandsById().get(target);
    if (command != null) {
      ctx.out().print(HelpRenderer.command(commandLine(ctx, command).getCommandSpec()));
      return 0;
    }
    if (topics().contains(target)) {
      ctx.out().print(HelpRenderer.topic(target, specs(ctx)));
      return 0;
    }
    Errors.printParseError(ctx.err(), "Command " + target + " not found.");
    return PARSE_ERROR_EXIT_CODE;
  }

  private static boolean wantsHelp(List<String> args) {
    for (String arg : args) {
      if (arg.equals("--")) {
        return false;
      }
      if (arg.equals("--help") || arg.equals("-h")) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, Class<?>> commandsById() {
    Map<String, Class<?>> result = new LinkedHashMap<>();
    for (Class<?> command : COMMANDS) {
      result.put(command.getAnnotation(CommandLine.Command.class).name(), command);
    }
    return result;
  }

  private static Set<String> topics() {
    Set<String> topics = new TreeSet<>();
    for (String id : commandsById().keySet()) {
      int colon = id.indexOf(':');
      if (colon > 0) {
        topics.add(id.substring(0, colon));
      }
    }
    return topics;
  }

  private static List<CommandSpec> specs(CliContext ctx) {
    List<CommandSpec> specs = new ArrayList<>();
    for (Class<?> command : COMMANDS) {
      specs.add(commandLine(ctx, command).getCommandSpec());
    }
    return specs;
  }

  private static CommandLine.IFactory factory(CliContext ctx) {
    CommandLine.IFactory fallback = CommandLine.defaultFactory();
    return new CommandLine.IFactory() {
      @Override
      public <K> K create(Class<K> cls) throws Exception {
        try {
          Constructor<K> constructor = cls.getConstructor(CliContext.class);
          return constructor.newInstance(ctx);
        } catch (NoSuchMethodException e) {
          return fallback.create(cls);
        }
      }
    };
  }
}
