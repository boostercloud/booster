package com.boostercloud.cli.common;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.services.Loggers;
import com.boostercloud.cli.services.ProjectChecker;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine;

/**
 * Base of every command except {@code new:project} ({@code common/base-command.ts}).
 *
 * <p>Lifecycle (oclif's {@code init → run → catch}): {@link #init()} checks that the current directory is a
 * Booster project whose version is compatible with the CLI, {@link #run()} executes the command, and any failure
 * goes through {@link #catchError(Throwable)}, which appends the full error to {@code ./errors.log} and prints only
 * its first line. The exit code is 1, or the exit code of the user's application when that is what failed.
 */
public abstract class BaseCommand implements Callable<Integer> {
  private static final Pattern ERROR_PREFIX = Pattern.compile("Error:", Pattern.LITERAL);

  /** The CLI context. */
  protected final CliContext ctx;

  /** The picocli model of the command (injected). */
  @CommandLine.Spec protected CommandLine.Model.CommandSpec spec;

  @CommandLine.Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "Show CLI help.")
  boolean help;

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  protected BaseCommand(CliContext ctx) {
    this.ctx = ctx;
  }

  /**
   * The command id, e.g. {@code new:entity} ({@code this.id}).
   *
   * @return the id
   */
  public String id() {
    if (spec != null) {
      return spec.name();
    }
    CommandLine.Command annotation = getClass().getAnnotation(CommandLine.Command.class);
    return annotation != null ? annotation.name() : getClass().getSimpleName();
  }

  /**
   * The arguments that followed the command id ({@code this.argv}).
   *
   * @return the arguments
   */
  public List<String> argv() {
    if (spec == null || spec.commandLine() == null || spec.commandLine().getParseResult() == null) {
      return List.of();
    }
    List<String> original = spec.commandLine().getParseResult().originalArgs();
    return !original.isEmpty() && original.getFirst().equals(id()) ? original.subList(1, original.size()) : original;
  }

  /**
   * Runs before {@link #run()}: checks the Booster version of the project in the current directory.
   *
   * @throws CliException when the project cannot be recognised or the versions are incompatible
   */
  public void init() {
    ProjectChecker.checkCurrentDirBoosterVersion(ctx, ctx.cliVersion());
  }

  /**
   * Executes the command.
   *
   * @return the exit code
   * @throws Exception any failure, handled by {@link #catchError(Throwable)}
   */
  public abstract int run() throws Exception;

  @Override
  public final Integer call() {
    try {
      init();
      return run();
    } catch (Exception | Error e) {
      return catchError(e);
    }
  }

  /**
   * Handles a failure: the first line of the message (without its first {@code Error:}) is printed on the
   * standard error with a reference to {@code ./errors.log}, where the command line and the full message are
   * appended.
   *
   * @param fullError the failure
   * @return the exit code of the CLI
   */
  protected int catchError(Throwable fullError) {
    String message = Errors.messageOf(fullError);
    String firstLine = message.split("\n", -1)[0];
    String errorMessage = ERROR_PREFIX.matcher(firstLine).replaceFirst(Matcher.quoteReplacement(""));
    String logRefMessage = "\n(You can see the full error logs in ./errors.log)";
    String errorForFile = "\nboost " + id() + " " + String.join(" ", argv()) + "\n" + message;
    try {
      Loggers.appendOnErrorsFile(ctx.cwd(), ctx.clock(), errorForFile);
    } catch (CliException e) {
      ctx.err().println(e.getMessage());
    }
    Errors.printError(ctx.err(), errorMessage + logRefMessage);
    return ProcessExitException.exitCodeOf(fullError);
  }
}
