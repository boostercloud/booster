package com.boostercloud.cli.commands.newcmd;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.Brand;
import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.common.Errors;
import com.boostercloud.cli.common.Provider;
import com.boostercloud.cli.common.Script;
import com.boostercloud.cli.services.FrameworkApi;
import com.boostercloud.cli.services.ProjectChecker;
import com.boostercloud.cli.services.ProjectInitializer;
import com.boostercloud.cli.services.ProjectInitializerConfig;
import com.boostercloud.cli.services.Prompter;
import com.boostercloud.cli.services.ProviderService;
import com.boostercloud.cli.services.java.JavaNames;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine;

/**
 * {@code boost new:project}: creates a new Maven project from scratch ({@code commands/new/project.ts}).
 *
 * <p>Unlike the other commands it does not need to run inside a project, so it does not extend
 * {@link com.boostercloud.cli.common.BaseCommand}: there is no version check and no {@code errors.log}. Every
 * runtime failure is printed on the standard error and the command finishes with exit code 0, as in TypeScript.
 */
@CommandLine.Command(name = "new:project", description = "create a new project from scratch")
public final class Project implements Callable<Integer> {
  /** The provider configured by {@code --default} (TypeScript uses the AWS provider, which is not ported). */
  public static final String DEFAULT_PROVIDER_PACKAGE = FrameworkApi.AZURE_PROVIDER_PACKAGE;

  private final CliContext ctx;

  @CommandLine.Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "Show CLI help.")
  boolean help;

  @CommandLine.Option(
      names = {"-d", "--description"},
      paramLabel = "<value>",
      description = "a short description")
  String description;

  @CommandLine.Option(
      names = {"-v", "--version"},
      paramLabel = "<value>",
      description = "the initial version")
  String version;

  @CommandLine.Option(
      names = {"-a", "--author"},
      paramLabel = "<value>",
      description = "who is writing this?")
  String author;

  @CommandLine.Option(
      names = {"-H", "--homepage"},
      paramLabel = "<value>",
      description = "the website of this project")
  String homepage;

  @CommandLine.Option(
      names = {"-l", "--license"},
      paramLabel = "<value>",
      description = "which license will you use?")
  String license;

  @CommandLine.Option(
      names = {"-r", "--repository"},
      paramLabel = "<value>",
      description = "the URL of the repository")
  String repository;

  @CommandLine.Option(
      names = {"-p", "--providerPackageName"},
      paramLabel = "<value>",
      description =
          "package name implementing the cloud provider integration where the application will be deployed (i.e:"
              + " \"com.boostercloud.framework.provider.azure\")")
  String providerPackageName;

  @CommandLine.Option(
      names = {"-g", "--groupId"},
      paramLabel = "<value>",
      description = "the Maven group id, also the root of the Java packages (i.e: \"com.example\")")
  String groupId;

  @CommandLine.Option(
      names = "--default",
      description = "generates the project with default parameters (i.e. --license=MIT)")
  boolean defaults;

  @CommandLine.Option(names = "--skipInstall", description = "skip dependencies installation")
  boolean skipInstall;

  @CommandLine.Option(names = "--skipGit", description = "skip git initialization")
  boolean skipGit;

  @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "PROJECTNAME", description = "name of the project")
  String projectName;

  /**
   * The values given on the command line ({@code Partial<ProjectInitializerConfig>} in TypeScript). Unset values
   * are {@code null}.
   *
   * @param projectName the project name
   * @param description {@code --description}
   * @param version {@code --version}
   * @param author {@code --author}
   * @param homepage {@code --homepage}
   * @param license {@code --license}
   * @param repository {@code --repository}
   * @param providerPackageName {@code --providerPackageName}
   * @param groupId {@code --groupId}
   * @param defaults {@code --default}
   * @param skipInstall {@code --skipInstall}
   * @param skipGit {@code --skipGit}
   */
  public record ProjectFlags(
      String projectName,
      String description,
      String version,
      String author,
      String homepage,
      String license,
      String repository,
      String providerPackageName,
      String groupId,
      boolean defaults,
      boolean skipInstall,
      boolean skipGit) {}

  /**
   * Creates the command.
   *
   * @param ctx the CLI context
   */
  public Project(CliContext ctx) {
    this.ctx = ctx;
  }

  @Override
  public Integer call() {
    try {
      if (projectName == null || projectName.isEmpty()) {
        ctx.err().println("You haven't provided a project name, but it is required, run with --help for usage");
        return 0;
      }
      ProviderService.assertNameIsCorrect(projectName);
      ProjectChecker.checkProjectAlreadyExists(ctx, projectName);
      ProjectFlags flags =
          new ProjectFlags(
              projectName,
              description,
              version,
              author,
              homepage,
              license,
              repository,
              providerPackageName,
              groupId,
              defaults,
              skipInstall,
              skipGit);
      run(ctx, flags, ctx.cliVersion());
    } catch (RuntimeException e) {
      ctx.err().println(withoutStackFrames(Errors.messageOf(e)));
    }
    return 0;
  }

  /**
   * Runs the project creation script.
   *
   * @param ctx the CLI context
   * @param flags the command-line values
   * @param boosterVersion the CLI version
   */
  public static void run(CliContext ctx, ProjectFlags flags, String boosterVersion) {
    Script.init(ctx.oraLogger(), "boost " + Brand.energize("new") + " 🚧", () -> parseConfig(ctx.prompter(), flags, boosterVersion))
        .step("Creating project root", config -> ProjectInitializer.generateRootDirectory(ctx, config))
        .step("Generating config files", config -> ProjectInitializer.generateConfigFiles(ctx, config))
        .optionalStep(flags.skipInstall(), "Installing dependencies", config -> ProjectInitializer.installDependencies(ctx, config))
        .optionalStep(flags.skipGit(), "Initializing git repository", config -> ProjectInitializer.initializeGit(ctx, config))
        .info("Project generated!")
        .done();
  }

  /**
   * Completes the project description: with {@code --default} every other value flag is ignored and the defaults
   * are used (Azure provider, version {@code 0.1.0}, MIT license); otherwise every missing value is asked in turn.
   * {@code --groupId}, which does not exist in TypeScript, is honoured in both cases.
   *
   * @param prompter asks the missing values
   * @param flags the command-line values
   * @param boosterVersion the CLI version
   * @return the project description
   * @throws CliException when the group id or the provider package is not valid
   */
  public static ProjectInitializerConfig parseConfig(Prompter prompter, ProjectFlags flags, String boosterVersion) {
    if (flags.defaults()) {
      return new ProjectInitializerConfig(
          flags.projectName(),
          "",
          "0.1.0",
          "",
          "",
          "MIT",
          "",
          DEFAULT_PROVIDER_PACKAGE,
          boosterVersion,
          true,
          flags.skipInstall(),
          flags.skipGit(),
          validGroupId(
              flags.groupId() == null || flags.groupId().isEmpty()
                  ? ProjectInitializerConfig.DEFAULT_GROUP_ID
                  : flags.groupId()));
    }
    String projectDescription =
        prompter.defaultOrPrompt(flags.description(), "What's your project description? (default: \"\")");
    String versionPrompt = prompter.defaultOrPrompt(flags.version(), "What's the first version? (default: 0.1.0)");
    String projectVersion = versionPrompt.isEmpty() ? "0.1.0" : versionPrompt;
    String projectAuthor = prompter.defaultOrPrompt(flags.author(), "Who's the author? (default: \"\")");
    String projectHomepage = prompter.defaultOrPrompt(flags.homepage(), "What's the website? (default: \"\")");
    String licensePrompt =
        prompter.defaultOrPrompt(flags.license(), "What license will you be publishing this under? (default: MIT)");
    String projectLicense = licensePrompt.isEmpty() ? "MIT" : licensePrompt;
    String projectRepository =
        prompter.defaultOrPrompt(flags.repository(), "What's the URL of the repository? (default: \"\")");
    String groupIdPrompt =
        prompter.defaultOrPrompt(
            flags.groupId(),
            "What's the Maven group id (the root of the Java packages)? (default: "
                + ProjectInitializerConfig.DEFAULT_GROUP_ID
                + ")");
    String projectGroupId =
        validGroupId(groupIdPrompt.isEmpty() ? ProjectInitializerConfig.DEFAULT_GROUP_ID : groupIdPrompt);
    String provider = normalizeProviderPackage(getProviderPackageName(prompter, flags.providerPackageName()));
    return new ProjectInitializerConfig(
        flags.projectName(),
        projectDescription,
        projectVersion,
        projectAuthor,
        projectHomepage,
        projectLicense,
        projectRepository,
        provider,
        boosterVersion,
        false,
        flags.skipInstall(),
        flags.skipGit(),
        projectGroupId);
  }

  /**
   * The package of a provider chosen in the list.
   *
   * @param provider the chosen provider
   * @return its package, or an empty string for {@link Provider#OTHER}
   */
  static String getSelectedProviderPackage(Provider provider) {
    return switch (provider) {
      case AZURE, LOCAL -> provider.packageName();
      case OTHER -> "";
    };
  }

  /**
   * The provider package: the flag value when given, otherwise the user's choice (and, for {@code Other}, the
   * package the user types).
   *
   * @param prompter asks the user
   * @param providerPackageName the flag value, may be {@code null}
   * @return the provider package as given or chosen
   */
  static String getProviderPackageName(Prompter prompter, String providerPackageName) {
    if (providerPackageName != null && !providerPackageName.isEmpty()) {
      return providerPackageName;
    }
    String selection =
        prompter.defaultOrChoose(
            providerPackageName,
            "What's the package name of your provider infrastructure library?",
            Arrays.stream(Provider.values()).map(Provider::label).toList());
    Provider provider = Provider.fromLabel(selection).orElse(null);
    if (provider == Provider.OTHER) {
      return prompter.defaultOrPrompt(
          null, "What's the other provider integration library? e.g. " + FrameworkApi.AZURE_PROVIDER_PACKAGE);
    }
    return provider == null ? "" : getSelectedProviderPackage(provider);
  }

  /**
   * Turns the provider given by the user into a Java package. The npm names of the TypeScript providers are
   * accepted for the ported ones.
   *
   * @param provider the provider as given
   * @return the Java package, or an empty string for a local-only project
   * @throws CliException when the provider is the AWS one or not a valid package name
   */
  static String normalizeProviderPackage(String provider) {
    String value = provider == null ? "" : provider.strip();
    switch (value) {
      case "" -> {
        return "";
      }
      case "@boostercloud/framework-provider-azure" -> {
        return FrameworkApi.AZURE_PROVIDER_PACKAGE;
      }
      case "@boostercloud/framework-provider-local" -> {
        return FrameworkApi.LOCAL_PROVIDER_PACKAGE;
      }
      case "@boostercloud/framework-provider-aws" ->
          throw new CliException(
              "The AWS provider is not available in Booster for Java. Use "
                  + FrameworkApi.AZURE_PROVIDER_PACKAGE
                  + " or "
                  + FrameworkApi.LOCAL_PROVIDER_PACKAGE);
      default -> {
        if (!JavaNames.isPackageName(value)) {
          throw new CliException(
              "'"
                  + value
                  + "' is not a valid provider package. Use the Java package of the provider, e.g. "
                  + FrameworkApi.AZURE_PROVIDER_PACKAGE);
        }
        return value;
      }
    }
  }

  private static String validGroupId(String groupId) {
    if (!JavaNames.isPackageName(groupId)) {
      throw new CliException(
          "'" + groupId + "' is not a valid Maven group id. It must be a Java package name, e.g. "
              + ProjectInitializerConfig.DEFAULT_GROUP_ID);
    }
    return groupId;
  }

  private static String withoutStackFrames(String message) {
    String text =
        message.lines().filter(line -> !line.startsWith("    at ")).collect(Collectors.joining(System.lineSeparator()));
    return text.startsWith("Error:") || text.startsWith("You haven't") ? text : "Error: " + text;
  }

  /** @return the values given on the command line (for tests) */
  ProjectFlags flags() {
    return new ProjectFlags(
        projectName, description, version, author, homepage, license, repository, providerPackageName, groupId,
        defaults, skipInstall, skipGit);
  }

  static List<String> providerLabels() {
    return Arrays.stream(Provider.values()).map(Provider::label).toList();
  }
}
