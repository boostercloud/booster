package com.boostercloud.cli.commands;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.BaseCommand;
import com.boostercloud.cli.common.Errors;
import picocli.CommandLine;

/**
 * Base of {@code build}, {@code clean}, {@code start}, {@code deploy}, {@code synth} and {@code nuke}: with
 * {@code --verbose} the complete error message is written to the standard error before the shortened one (each of
 * those TypeScript commands overrides {@code catch} the same way).
 */
public abstract class VerboseCommand extends BaseCommand {
  @CommandLine.Option(names = "--verbose", description = "display full error messages")
  boolean verbose;

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  protected VerboseCommand(CliContext ctx) {
    super(ctx);
  }

  @Override
  protected int catchError(Throwable fullError) {
    if (verbose) {
      ctx.err().println(Errors.messageOf(fullError));
    }
    return super.catchError(fullError);
  }
}
