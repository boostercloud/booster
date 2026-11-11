package com.boostercloud.cli.integration;

import static com.boostercloud.cli.integration.helper.Expectations.assertFailsWithMessage;
import static com.boostercloud.cli.integration.helper.Expectations.assertOutputSteps;
import static com.boostercloud.cli.integration.helper.Expectations.assertSucceeds;
import static com.boostercloud.cli.integration.helper.FileHelper.dirContents;
import static com.boostercloud.cli.integration.helper.FileHelper.fileExists;
import static com.boostercloud.cli.integration.helper.FileHelper.loadFixture;
import static com.boostercloud.cli.integration.helper.FileHelper.readFileContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.integration.helper.Boost;
import com.boostercloud.cli.integration.helper.CliResult;
import com.boostercloud.cli.integration.helper.CliTestEnvironment;
import com.boostercloud.cli.integration.helper.FileHelper;
import com.boostercloud.cli.integration.helper.FileHelper.Replacement;
import com.boostercloud.cli.integration.helper.Processes;
import com.boostercloud.cli.integration.helper.PromptAnswer;
import com.boostercloud.cli.integration.helper.Sandbox;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Port of {@code cli.project.integration.ts}: {@code boost new:project} with flags, with the interactive prompts and
 * with both, and its validation errors.
 *
 * <p>The TypeScript fixtures ({@code cart-demo/package.json}, {@code tsconfig.json}, {@code .eslintrc.js}...) become
 * the files of a Java project ({@code cart-demo/pom.xml}, {@code Main.java}, {@code Config.java},
 * {@code gitignore}, {@code README.md}), compared byte for byte. The {@code package.json} comparison (only the keys
 * of the dependencies, the Booster versions equal to {@code ^BOOSTER_VERSION}) becomes the exact comparison of the
 * {@code pom.xml}, whose {@code booster.version} property is the CLI version.
 *
 * <p>Java differences, all from the CLI: the AWS provider is not ported, so {@link #PROVIDER} is the Azure provider
 * (which, like AWS in TypeScript, is also the first choice of the provider prompt); the {@code -g/--groupId} flag
 * and prompt are new (the flag tests pass it, the prompt tests answer it with the default); the {@code Other}
 * provider of the list is chosen by its number and then typed (inquirer: arrow down, then the name); an invalid
 * provider package is rejected before anything is created, where TypeScript only fails when npm installs it.
 */
@DisplayName("Project")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class CliProjectIT {
  static final String BOOSTER_VERSION = CliTestEnvironment.boosterVersion();
  static final String DESCRIPTION = "cart-demo";
  static final String VERSION = "1.0.0";
  static final String AUTHOR = "The Agile Monkeys";
  static final String HOMEPAGE = "https://www.boosterframework.com/";
  static final String LICENSE = "Apache";
  static final String REPO_URL = "https://github.com/boostercloud/booster/";
  /** TypeScript: {@code @boostercloud/framework-provider-aws}. */
  static final String PROVIDER = "com.boostercloud.framework.provider.azure";
  /** Java only: the Maven group id and root package (the default of the CLI). */
  static final String GROUP_ID = "com.example";

  static final String[] EXPECTED_OUTPUT = {
    "boost new",
    "Creating project root",
    "Generating config files",
    "Installing dependencies",
    "Initializing git repository",
    "Project generated"
  };

  final Path sandboxIntegrationDir = FileHelper.sandboxPathFor("new-project");

  @BeforeAll
  void createSandbox() {
    // TypeScript sets user.name/user.email in the global git configuration of the machine when they are missing;
    // the CLI runs here with GIT_CONFIG_GLOBAL pointing at a configuration of its own (see Boost).
    FileHelper.removeFolders(List.of(sandboxIntegrationDir));
    FileHelper.createFolder(sandboxIntegrationDir);
  }

  @AfterAll
  void removeSandbox() {
    Processes.killAll();
    Sandbox.removeSandboxProject(sandboxIntegrationDir);
  }

  @Nested
  @Order(1)
  @DisplayName("Valid project")
  @TestClassOrder(ClassOrderer.OrderAnnotation.class)
  class ValidProject {

    @Nested
    @Order(1)
    @DisplayName("using flags")
    @TestClassOrder(ClassOrderer.OrderAnnotation.class)
    class UsingFlags {

      @Test
      @DisplayName("should create a new project using short flags to configure it")
      void shortFlags() {
        String projectName = "cart-demo-short-flags";
        List<String> flags =
            List.of(
                "-a", AUTHOR,
                "-d", DESCRIPTION,
                "-H", HOMEPAGE,
                "-l", LICENSE,
                "-p", PROVIDER,
                "-r", REPO_URL,
                "-v", VERSION,
                "-g", GROUP_ID,
                // We skip dependencies and git installation to make this test faster
                "--skipInstall",
                "--skipGit");
        CliResult output = execNewProject(projectName, flags);

        assertions(output, projectName, flags);
        assertThat(output.stdout()).as("no question is asked when every value is given").doesNotContain("? ");
      }

      @Test
      @DisplayName("should create a new project using long flags to configure it")
      void longFlags() {
        String projectName = "cart-demo-long-flags";
        List<String> flags =
            List.of(
                "--author", AUTHOR,
                "--description", DESCRIPTION,
                "--homepage", HOMEPAGE,
                "--license", LICENSE,
                "--providerPackageName", PROVIDER,
                "--repository", REPO_URL,
                "--version", VERSION,
                "--groupId", GROUP_ID,
                // We skip dependencies and git installation to make this test faster
                "--skipInstall",
                "--skipGit");
        CliResult output = execNewProject(projectName, flags);

        assertions(output, projectName, flags);
        assertThat(output.stdout()).doesNotContain("? ");
      }

      @Test
      @DisplayName("should accept the npm name of a ported provider (Java)")
      void npmProviderName() {
        String projectName = "cart-demo-npm-provider";
        List<String> flags =
            List.of(
                "-a", AUTHOR,
                "-d", DESCRIPTION,
                "-H", HOMEPAGE,
                "-l", LICENSE,
                "-p", "@boostercloud/framework-provider-azure",
                "-r", REPO_URL,
                "-v", VERSION,
                "-g", GROUP_ID,
                "--skipInstall",
                "--skipGit");

        assertions(execNewProject(projectName, flags), projectName, flags);
      }

      @Nested
      @Order(1)
      @DisplayName("with default parameters")
      @TestInstance(TestInstance.Lifecycle.PER_CLASS)
      @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
      class WithDefaultParameters {
        final String projectName = "cart-demo-default";
        final List<String> flags = List.of("--default", "--skipInstall");
        CliResult output;

        @BeforeAll
        void createProject() {
          output = execNewProject(projectName, flags);
        }

        @Test
        @Order(1)
        @DisplayName("generates the expected project")
        void generatesTheExpectedProject() {
          assertions(output, projectName, flags);
        }

        // TypeScript leaves this test empty: the `workspace:` protocol cannot be installed outside the monorepo.
        // Here --skipInstall skips the step, which the output says; CliStartIT installs the dependencies for real.
        @Test
        @Order(2)
        @DisplayName("installs dependencies")
        void installsDependencies() {
          assertThat(output.stdout()).contains("Skipping: Installing dependencies");
          assertThat(projectPath(projectName, "target")).doesNotExist();
        }

        @Test
        @Order(3)
        @DisplayName("initializes git")
        void initializesGit() {
          assertThat(projectFileExists(projectName, ".git")).isTrue();
          // Java: the initial commit holds every generated file
          assertThat(git(projectPath(projectName), "rev-list", "--count", "HEAD")).isEqualTo("1");
          assertThat(git(projectPath(projectName), "status", "--porcelain")).isEmpty();
        }

        // TypeScript runs `npm run build` after rewriting the dependencies to the local packages; here the project
        // is built by the CLI (mvn package) against the framework artifacts of the isolated repository.
        @Test
        @Order(4)
        @DisplayName("compiles")
        void compiles() {
          CliResult build = Boost.run(projectPath(projectName), "build");

          assertSucceeds(build, "boost build", "Checking project structure", "Building project", "Build complete");
          assertThat(projectPath(projectName, "target/classes/com/example/cartdemodefault/Main.class")).exists();
          assertThat(projectPath(projectName, "target/classes/com/example/cartdemodefault/config/Config.class"))
              .exists();
          assertThat(projectPath(projectName, "target/lib/booster-framework-core-" + BOOSTER_VERSION + ".jar"))
              .exists();
        }

        // TypeScript runs `npm run lint:check` (eslint). A generated Java project has no linter configured, so the
        // check is javac's: every lint category, warnings as errors, against the dependencies copied by "compiles"
        // (which therefore runs first, unlike in TypeScript).
        @Test
        @Order(5)
        @DisplayName("passes linter")
        void passesLinter() throws Exception {
          Path project = projectPath(projectName);
          List<String> command = new ArrayList<>();
          command.add(Path.of(System.getProperty("java.home"), "bin", "javac").toString());
          command.addAll(
              List.of(
                  "-Xlint:all,-processing,-classfile",
                  "-Werror",
                  "--release",
                  "27",
                  "-parameters",
                  "-proc:none",
                  "-d",
                  project.resolve("target/lint-classes").toString(),
                  "-cp",
                  project.resolve("target/lib") + File.separator + "*"));
          try (Stream<Path> sources = Files.walk(project.resolve("src/main/java"))) {
            sources.filter(p -> p.toString().endsWith(".java")).map(Path::toString).forEach(command::add);
          }
          Process javac = new ProcessBuilder(command).directory(project.toFile()).redirectErrorStream(true).start();
          String lintOutput = new String(javac.getInputStream().readAllBytes());

          assertThat(javac.waitFor()).as(lintOutput).isZero();
          assertThat(lintOutput).isEmpty();
        }
      }

      @Test
      @DisplayName("initializes a git repo")
      void initializesAGitRepo() {
        String projectName = "cart-demo-with-git";
        // We skip dependencies installation to make the test faster
        List<String> flags = List.of("--default", "--skipInstall");

        execNewProject(projectName, flags);
        // Java: TypeScript only checks that the command does not fail
        assertThat(projectFileExists(projectName, ".git")).isTrue();
      }
    }

    @Nested
    @Order(2)
    @DisplayName("using command prompt")
    class UsingCommandPrompt {

      @Test
      @DisplayName("should create a new project")
      void shouldCreateANewProject() {
        String projectName = "cart-demo-command-prompt";
        List<PromptAnswer> promptAnswers =
            List.of(
                PromptAnswer.of("description", DESCRIPTION),
                PromptAnswer.of("version", VERSION),
                PromptAnswer.of("author", AUTHOR),
                PromptAnswer.of("website", HOMEPAGE),
                PromptAnswer.of("license", LICENSE),
                PromptAnswer.of("repository", REPO_URL),
                PromptAnswer.of("group id", ""), // Java: the default group
                PromptAnswer.of("provider", "")); // Just "hit enter" to choose the default one
        // We skip dependencies and git installation to make this test faster
        CliResult output = execNewProject(projectName, List.of("--skipInstall", "--skipGit"), promptAnswers);

        assertions(output, projectName, List.of());
      }

      @Test
      @DisplayName("should create a new project using a custom provider")
      void customProvider() {
        String projectName = "cart-demo-custom-provider";
        List<PromptAnswer> promptAnswers =
            List.of(
                PromptAnswer.of("description", DESCRIPTION),
                PromptAnswer.of("version", VERSION),
                PromptAnswer.of("author", AUTHOR),
                PromptAnswer.of("website", HOMEPAGE),
                PromptAnswer.of("license", LICENSE),
                PromptAnswer.of("repository", REPO_URL),
                PromptAnswer.of("group id", GROUP_ID),
                // TypeScript: arrow down, then the package name; here "3) Other" and then the package name
                PromptAnswer.of("provider", "3"),
                PromptAnswer.of("other provider integration library", PROVIDER));
        // We skip dependencies and git installation to make this test faster
        CliResult output = execNewProject(projectName, List.of("--skipInstall", "--skipGit"), promptAnswers);

        assertions(output, projectName, List.of());
      }
    }

    @Nested
    @Order(3)
    @DisplayName("using flags and command prompt")
    class UsingFlagsAndCommandPrompt {

      @Test
      @DisplayName("should create a new project")
      void shouldCreateANewProject() {
        String projectName = "cart-demo-flags-and-command-prompt";
        List<PromptAnswer> promptAnswers =
            List.of(
                PromptAnswer.of("description", DESCRIPTION),
                PromptAnswer.of("version", VERSION),
                PromptAnswer.of("author", AUTHOR),
                PromptAnswer.of("website", HOMEPAGE),
                PromptAnswer.of("license", LICENSE));
        List<String> flags =
            List.of(
                "--providerPackageName", PROVIDER,
                "--repository", REPO_URL,
                // We skip dependencies and git installation to make this test faster
                "--skipInstall",
                "--skipGit");
        // The group id prompt is not answered: the input is closed after the last answer, which selects the default
        CliResult output = execNewProject(projectName, flags, promptAnswers);

        assertions(output, projectName, flags);
        assertThat(output.stdout()).as("the flags answer the repository and provider questions")
            .doesNotContain("repository?").doesNotContain("provider infrastructure library");
      }
    }
  }

  @Nested
  @Order(2)
  @DisplayName("Invalid project")
  class InvalidProject {

    @Test
    @DisplayName("missing project name > should fail")
    void missingProjectName() {
      CliResult result = Boost.command(sandboxIntegrationDir, "new:project");

      assertFailsWithMessage(
          result, "You haven't provided a project name, but it is required, run with --help for usage");
    }

    // TypeScript: "invalid-provider" is written to package.json and the generation goes on until the dependencies
    // are installed. The Java CLI validates the provider package first, so nothing is created.
    @Test
    @DisplayName("using an invalid provider > should fail")
    void invalidProvider() {
      String projectName = "cart-demo-invalid-provider";
      CliResult output =
          Boost.run(
              sandboxIntegrationDir,
              List.of(
                  "new:project", projectName,
                  "--author", AUTHOR,
                  "--description", DESCRIPTION,
                  "--homepage", HOMEPAGE,
                  "--license", LICENSE,
                  "--providerPackageName", "invalid-provider",
                  "--repository", REPO_URL,
                  "--version", VERSION,
                  "--groupId", GROUP_ID,
                  // We skip dependencies and git installation to make this test faster
                  "--skipInstall",
                  "--skipGit"));

      assertFailsWithMessage(
          output,
          "Error: 'invalid-provider' is not a valid provider package. Use the Java package of the provider, e.g."
              + " com.boostercloud.framework.provider.azure");
      assertThat(projectPath(projectName)).doesNotExist();
    }

    // What the TypeScript test pins: a provider that does not exist is not checked while generating
    @Test
    @DisplayName("using an unknown provider package > generates the project up to the installation")
    void unknownProvider() {
      CliResult output =
          execNewProject(
              "cart-demo-unknown-provider",
              List.of(
                  "--author", AUTHOR,
                  "--description", DESCRIPTION,
                  "--homepage", HOMEPAGE,
                  "--license", LICENSE,
                  "--providerPackageName", "com.example.invalid.provider",
                  "--repository", REPO_URL,
                  "--version", VERSION,
                  "--groupId", GROUP_ID,
                  "--skipInstall",
                  "--skipGit"));

      assertOutputSteps(output, "boost new", "Creating project root", "Generating config files",
          "Installing dependencies");
      assertThat(readFileContent(configFile("cart-demo-unknown-provider")))
          .contains("config.providerPackage = \"com.example.invalid.provider\";");
    }

    @Test
    @DisplayName("using the AWS provider > should fail (Java)")
    void awsProvider() {
      CliResult output =
          Boost.run(
              sandboxIntegrationDir,
              List.of("new:project", "cart-demo-aws", "-p", "@boostercloud/framework-provider-aws", "--default",
                  "--skipInstall", "--skipGit"));

      // --default ignores every other value flag, as in TypeScript
      assertThat(output.exitCode()).as(output.describe()).isZero();
      assertThat(readFileContent(configFile("cart-demo-aws")))
          .doesNotContain("provider-aws")
          .contains("config.providerPackage = \"com.boostercloud.framework.provider.azure\";");

      CliResult explicit =
          Boost.run(
              sandboxIntegrationDir,
              List.of("new:project", "cart-demo-aws-explicit", "-p", "@boostercloud/framework-provider-aws",
                  "-d", DESCRIPTION, "-v", VERSION, "-a", AUTHOR, "-H", HOMEPAGE, "-l", LICENSE, "-r", REPO_URL,
                  "-g", GROUP_ID, "--skipInstall", "--skipGit"));
      assertFailsWithMessage(
          explicit,
          "Error: The AWS provider is not available in Booster for Java. Use com.boostercloud.framework.provider.azure"
              + " or com.boostercloud.framework.provider.local");
      assertThat(projectPath("cart-demo-aws-explicit")).doesNotExist();
    }

    @Test
    @DisplayName("using an existing folder > should fail without overwriting it (Java)")
    void existingFolder() {
      String projectName = "cart-demo-existing";
      FileHelper.writeFileContent(projectPath(projectName, "keep.txt"), "keep");

      CliResult output = Boost.run(sandboxIntegrationDir, List.of("new:project", projectName, "--default"));

      assertThat(output.stdout()).contains("Project folder \"" + projectName + "\" already exists.");
      assertFailsWithMessage(
          output, "Error: The folder you're trying to use already exists. Please use another project name");
      assertThat(dirContents(projectPath(projectName))).containsExactly("keep.txt");
    }

    @Test
    @DisplayName("using an invalid project name > should fail (Java)")
    void invalidProjectName() {
      CliResult output = Boost.run(sandboxIntegrationDir, List.of("new:project", "Cart_Demo", "--default"));

      assertFailsWithMessage(output, "Project name cannot contain uppercase letters:\n\n    Found: 'Cart_Demo'");
      assertThat(projectPath("Cart_Demo")).doesNotExist();
    }
  }

  CliResult execNewProject(String projectName, List<String> flags) {
    return execNewProject(projectName, flags, null);
  }

  /** {@code execNewProject}: rejects (fails) when the CLI exits with a non-zero code. */
  CliResult execNewProject(String projectName, List<String> flags, List<PromptAnswer> promptAnswers) {
    List<String> arguments = new ArrayList<>();
    arguments.add("new:project");
    arguments.add(projectName);
    arguments.addAll(flags);
    CliResult result =
        promptAnswers == null
            ? Boost.run(sandboxIntegrationDir, arguments)
            : Boost.runWithPrompts(sandboxIntegrationDir, arguments, promptAnswers);
    assertThat(result.exitCode()).as(result.describe()).isZero();
    return result;
  }

  Path projectPath(String projectName) {
    return sandboxIntegrationDir.resolve(projectName);
  }

  Path projectPath(String projectName, String fileName) {
    return projectPath(projectName).resolve(fileName);
  }

  boolean projectFileExists(String projectName, String fileName) {
    return fileExists(projectPath(projectName, fileName));
  }

  /** The package of the generated main class: {@code com.example.<project name without separators>}. */
  static String basePackageName(String projectName) {
    return projectName.replaceAll("[^a-z0-9]", "");
  }

  Path sourcesDir(String projectName) {
    return projectPath(projectName, "src/main/java/com/example/" + basePackageName(projectName));
  }

  Path configFile(String projectName) {
    return sourcesDir(projectName).resolve("config/Config.java");
  }

  void assertions(CliResult output, String projectName, List<String> flags) {
    assertThat(output.stderr()).as(output.describe()).isEmpty();
    assertOutputSteps(output, EXPECTED_OUTPUT);

    Path sources = sourcesDir(projectName);
    for (String resourcePackage :
        List.of("commands", "common", "entities", "eventhandlers", "events", "queries", "readmodels",
            "scheduledcommands")) {
      assertThat(dirContents(sources.resolve(resourcePackage))).as(resourcePackage).isEmpty();
    }
    assertThat(dirContents(sources.resolve("config"))).hasSize(1);
    assertThat(dirContents(projectPath(projectName, "src/main/resources"))).isEmpty();

    boolean defaults = flags.contains("--default");
    Replacement[] replacements = {
      Replacement.of("project_name_placeholder", projectName),
      Replacement.of("base_package_placeholder", basePackageName(projectName)),
      Replacement.of("booster_version_placeholder", BOOSTER_VERSION),
      Replacement.of("description_placeholder", defaults ? "" : DESCRIPTION),
      Replacement.of("version_placeholder", defaults ? "0.1.0" : VERSION),
      Replacement.of("author_placeholder", defaults ? "" : AUTHOR),
      Replacement.of("homepage_placeholder", defaults ? "" : HOMEPAGE),
      Replacement.of("license_placeholder", defaults ? "MIT" : LICENSE),
      Replacement.of("repository_placeholder", defaults ? "" : REPO_URL)
    };

    assertThat(readFileContent(configFile(projectName))).isEqualTo(loadFixture("cart-demo/Config.java", replacements));
    assertThat(readFileContent(sources.resolve("Main.java"))).isEqualTo(loadFixture("cart-demo/Main.java", replacements));
    assertThat(readFileContent(projectPath(projectName, ".gitignore"))).isEqualTo(loadFixture("cart-demo/gitignore"));
    // Without a description the README has no description paragraph
    String expectedReadme =
        defaults
            ? loadFixture("cart-demo/README.md", replacements(projectName, Replacement.of("description_placeholder\n\n", "")))
            : loadFixture("cart-demo/README.md", replacements);
    assertThat(readFileContent(projectPath(projectName, "README.md"))).isEqualTo(expectedReadme);

    String pom = readFileContent(projectPath(projectName, "pom.xml"));
    assertThat(pom).isEqualTo(loadFixture(defaults ? "cart-demo/pom-default.xml" : "cart-demo/pom.xml", replacements));
    // package.json: dependencies['@boostercloud/framework-core'] === `^${BOOSTER_VERSION}`
    assertThat(pom).contains("<booster.version>" + BOOSTER_VERSION + "</booster.version>");
  }

  /** The given replacements followed by those of the project name, the package and the Booster version. */
  static Replacement[] replacements(String projectName, Replacement... first) {
    List<Replacement> all = new ArrayList<>(List.of(first));
    all.add(Replacement.of("project_name_placeholder", projectName));
    all.add(Replacement.of("base_package_placeholder", basePackageName(projectName)));
    all.add(Replacement.of("booster_version_placeholder", BOOSTER_VERSION));
    return all.toArray(Replacement[]::new);
  }

  /** Runs git in a generated project with the identity the CLI used. */
  static String git(Path directory, String... arguments) {
    List<String> command = new ArrayList<>();
    command.add("git");
    command.addAll(List.of(arguments));
    try {
      ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
      builder.environment().putAll(Boost.environment());
      Process process = builder.start();
      String output = new String(process.getInputStream().readAllBytes()).strip();
      assertThat(process.waitFor()).as(output).isZero();
      return output;
    } catch (Exception e) {
      throw new AssertionError("git " + String.join(" ", arguments) + " failed", e);
    }
  }
}
