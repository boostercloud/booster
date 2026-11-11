package com.boostercloud.cli.commands.stub;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.BaseCommand;
import com.boostercloud.cli.common.Brand;
import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.common.Script;
import com.boostercloud.cli.services.ProjectChecker;
import com.boostercloud.cli.services.StubPublisher;
import picocli.CommandLine;

/**
 * {@code boost stub:publish}: copies the built-in resource templates to {@code <project>/stubs}, where they can be
 * customised; the generators use them instead of the built-in ones ({@code commands/stub/publish.ts}).
 */
@CommandLine.Command(
    name = "stub:publish",
    description = "publish all resource template stubs that are available for customization",
    customSynopsis = "stub:publish --force",
    footer = {"$ boost stub:publish --force", "$ boost stub:publish"})
public final class Publish extends BaseCommand {
  @CommandLine.Option(
      names = {"-f", "--force"},
      description = "Overwrite any existing stub files")
  boolean force;

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public Publish(CliContext ctx) {
    super(ctx);
  }

  @Override
  public int run() {
    boolean stubFolderExists = StubPublisher.checkStubsFolderExists(ctx.cwd());
    if (!stubFolderExists) {
      StubPublisher.createStubsFolder(ctx.cwd());
    }
    if (stubFolderExists && !force) {
      boolean confirm =
          ctx.prompter().confirmPrompt(Brand.dangerize("Stubs folder already exists. Do you want to overwrite it?"));
      if (!confirm) {
        throw new CliException(
            Brand.dangerize("Stubs folder already exists. Use --force option to overwrite files in it"));
      }
    }
    run(ctx);
    return 0;
  }

  /**
   * Runs the publication script.
   *
   * @param ctx the CLI context
   */
  public static void run(CliContext ctx) {
    Script.init(ctx.oraLogger(), "boost " + Brand.energize("stub:publish") + " 🗄", ctx::cwd)
        .step("Verifying project", cwd -> ProjectChecker.checkCurrentDirIsABoosterProject(ctx))
        .step("Publishing stubs", StubPublisher::publishStubFiles)
        .info("Resource template stubs published!")
        .done();
  }
}
