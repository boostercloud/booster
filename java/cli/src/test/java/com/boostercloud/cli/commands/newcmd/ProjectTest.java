package com.boostercloud.cli.commands.newcmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.common.ColorSupport;
import com.boostercloud.cli.services.FrameworkApi;
import com.boostercloud.cli.services.ProjectInitializerConfig;
import com.boostercloud.cli.services.Prompter;
import com.boostercloud.cli.services.project.PomFile;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Port of {@code test/commands/new/project.test.ts}. */
class ProjectTest {
  @TempDir Path dir;

  private static final String PROJECT_NAME = "test-project";
  private TestCli cli;

  @BeforeEach
  void setUp() {
    cli = new TestCli(dir);
  }

  private void expectFilesAndDirectoriesCreated() {
    Path base = dir.resolve(PROJECT_NAME).resolve("src/main/java/com/example/testproject");
    for (String pkg :
        List.of("commands", "events", "entities", "readmodels", "config", "common", "eventhandlers", "scheduledcommands", "queries")) {
      assertThat(base.resolve(pkg)).isDirectory();
    }
    assertThat(dir.resolve(PROJECT_NAME).resolve(".gitignore")).isRegularFile();
    assertThat(dir.resolve(PROJECT_NAME).resolve("pom.xml")).isRegularFile();
    assertThat(dir.resolve(PROJECT_NAME).resolve("README.md")).isRegularFile();
    assertThat(base.resolve("Main.java")).isRegularFile();
    assertThat(base.resolve("config/Config.java")).isRegularFile();
  }

  private PomFile pom() {
    return PomFile.read(dir.resolve(PROJECT_NAME).resolve("pom.xml"));
  }

  @Nested
  class WorksProperly {
    @Test
    void withDefaultFlag() {
      TestCli.Result result = cli.run("new:project", PROJECT_NAME, "--default");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout()).contains("ℹ boost new 🚧").contains("ℹ Project generated!");
      expectFilesAndDirectoriesCreated();
      assertThat(cli.process().lines())
          .containsExactly("mvn -B -q dependency:resolve", "git init", "git add -A", "git commit -m Initial commit");
      assertThat(pom().artifactId()).isEqualTo(PROJECT_NAME);
    }

    @Test
    void skipsDependenciesInstallationWithSkipInstall() {
      TestCli.Result result = cli.run("new:project", PROJECT_NAME, "--default", "--skipInstall");
      assertThat(result.stdout()).contains("ℹ Skipping: Installing dependencies").contains("Project generated!");
      assertThat(cli.process().lines()).containsExactly("git init", "git add -A", "git commit -m Initial commit");
      expectFilesAndDirectoriesCreated();
    }

    @Test
    void skipsGitRepositoryInitializationWithSkipGit() {
      TestCli.Result result = cli.run("new:project", PROJECT_NAME, "--default", "--skipGit");
      assertThat(result.stdout()).contains("ℹ Skipping: Initializing git repository");
      assertThat(cli.process().lines()).containsExactly("mvn -B -q dependency:resolve");
    }

    @Test
    void withoutFlagsAsksEveryValue() {
      cli.stdin("A store\n1.0.0\nJane\nhttps://store.example\nApache-2.0\nhttps://github.com/acme/store\norg.acme\n1\n");
      TestCli.Result result = cli.run("new:project", PROJECT_NAME, "--skipInstall", "--skipGit");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout())
          .contains("? What's your project description? (default: \"\")")
          .contains("? What's the first version? (default: 0.1.0)")
          .contains("? Who's the author? (default: \"\")")
          .contains("? What's the website? (default: \"\")")
          .contains("? What license will you be publishing this under? (default: MIT)")
          .contains("? What's the URL of the repository? (default: \"\")")
          .contains("? What's the Maven group id (the root of the Java packages)? (default: com.example)")
          .contains("? What's the package name of your provider infrastructure library?");
      PomFile pom = pom();
      assertThat(pom.groupId()).isEqualTo("org.acme");
      assertThat(pom.version()).isEqualTo("1.0.0");
      assertThat(pom.property("booster.mainClass")).contains("org.acme.testproject.Main");
      assertThat(TestProjects.read(dir.resolve(PROJECT_NAME).resolve("pom.xml")))
          .contains("<description>A store</description>")
          .contains("<url>https://store.example</url>")
          .contains("<name>Apache-2.0</name>")
          .contains("<name>Jane</name>")
          .contains("<url>https://github.com/acme/store</url>")
          .contains("booster-framework-provider-azure-infrastructure");
    }

    @Test
    void definesEveryValueWithLongFlags() {
      TestCli.Result result =
          cli.run(
              "new:project", PROJECT_NAME, "--description", "a short description", "--version", "2.0.0", "--author",
              "John Doe", "--homepage", "boosterframework.com", "--license", "MIT", "--repository",
              "github.com:boostercloud/booster.git", "--providerPackageName", FrameworkApi.LOCAL_PROVIDER_PACKAGE,
              "--groupId", "com.boost", "--skipInstall", "--skipGit");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout()).doesNotContain("?");
      String pom = TestProjects.read(dir.resolve(PROJECT_NAME).resolve("pom.xml"));
      assertThat(pom)
          .contains("<description>a short description</description>")
          .contains("<version>2.0.0</version>")
          .contains("<name>John Doe</name>")
          .contains("<url>boosterframework.com</url>")
          .contains("<url>github.com:boostercloud/booster.git</url>")
          .doesNotContain("azure");
      assertThat(TestProjects.read(dir.resolve(PROJECT_NAME).resolve("src/main/java/com/boost/testproject/config/Config.java")))
          .doesNotContain("production");
    }

    @Test
    void definesEveryValueWithShortFlags() {
      TestCli.Result result =
          cli.run(
              "new:project", PROJECT_NAME, "-d", "a short description", "-v", "2.0.0", "-a", "John Doe", "-H",
              "boosterframework.com", "-l", "MIT", "-r", "github.com:boostercloud/booster.git", "-p",
              "@boostercloud/framework-provider-azure", "-g", "com.boost", "--skipInstall", "--skipGit");
      assertThat(result.exitCode()).isZero();
      assertThat(TestProjects.read(dir.resolve(PROJECT_NAME).resolve("pom.xml")))
          .contains("<name>John Doe</name>")
          .contains("booster-framework-provider-azure");
    }

    @Test
    void asksBeforeOverwritingAnExistingFolder() {
      TestProjects.write(dir.resolve(PROJECT_NAME).resolve("old.txt"), "old");
      cli.stdin("y\n");
      TestCli.Result result = cli.run("new:project", PROJECT_NAME, "--default", "--skipInstall", "--skipGit");
      assertThat(result.stdout()).contains("Project folder \"test-project\" already exists. Do you want to overwrite it?");
      assertThat(dir.resolve(PROJECT_NAME).resolve("old.txt")).doesNotExist();
      expectFilesAndDirectoriesCreated();
    }
  }

  @Nested
  class DisplaysAnError {
    @Test
    void withEmptyProjectName() {
      TestCli.Result result = cli.run("new:project");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stderr()).contains("You haven't provided a project name, but it is required, run with --help for usage");
    }

    @Test
    void withNonexistingOption() {
      TestCli.Result result = cli.run("new:project", PROJECT_NAME, "--nonexistingoption");
      assertThat(result.exitCode()).isEqualTo(2);
      assertThat(result.stderr()).contains("Nonexistent flag: --nonexistingoption");
      assertThat(result.stdout()).doesNotContain("Project generated!");
      assertThat(dir.resolve(PROJECT_NAME)).doesNotExist();
    }

    @ParameterizedTest
    @CsvSource({
      "--homepage, --homepage",
      "-H, --homepage",
      "--author, --author",
      "-a, --author",
      "--description, --description",
      "-d, --description",
      "--license, --license",
      "-l, --license",
      "--providerPackageName, --providerPackageName",
      "-p, --providerPackageName",
      "--repository, --repository",
      "-r, --repository",
      "--version, --version",
      "-v, --version",
      "--groupId, --groupId"
    })
    void withAFlagAndNoValue(String flag, String longName) {
      TestCli.Result result = cli.run("new:project", PROJECT_NAME, flag);
      assertThat(result.exitCode()).isEqualTo(2);
      assertThat(result.stderr()).contains(longName + " expects a value");
    }

    @Test
    void withAnInvalidProjectName() {
      TestCli.Result result = cli.run("new:project", "Test Project", "--default");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stderr()).contains("Error: Project name cannot contain spaces:\n\n    Found: 'Test Project'");
      assertThat(dir.resolve("Test Project")).doesNotExist();
    }

    @Test
    void whenTheUserRefusesToOverwrite() {
      TestProjects.write(dir.resolve(PROJECT_NAME).resolve("old.txt"), "old");
      TestCli.Result result = cli.stdin("n\n").run("new:project", PROJECT_NAME, "--default");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stderr()).contains("The folder you're trying to use already exists. Please use another project name");
      assertThat(dir.resolve(PROJECT_NAME).resolve("old.txt")).exists();
    }

    @Test
    void whenAStepFailsTheProjectIsKeptAndTheErrorPrinted() {
      cli.process().failWhen(cmd -> cmd.contains("dependency:resolve"), 1, "Could not resolve dependencies");
      TestCli.Result result = cli.run("new:project", PROJECT_NAME, "--default");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout()).contains("✖ Installing dependencies").doesNotContain("Project generated!");
      assertThat(result.stderr()).contains("Could not install dependencies").doesNotContain("    at ");
      assertThat(cli.process().lines()).doesNotContain("git init");
      expectFilesAndDirectoriesCreated();
    }

    @Test
    void withTheAwsProvider() {
      TestCli.Result result =
          cli.run("new:project", PROJECT_NAME, "-p", "@boostercloud/framework-provider-aws", "--skipInstall", "--skipGit");
      assertThat(result.stderr()).contains("The AWS provider is not available in Booster for Java");
      assertThat(dir.resolve(PROJECT_NAME).resolve("pom.xml")).doesNotExist();
    }

    @Test
    void withAnInvalidGroupId() {
      TestCli.Result result = cli.run("new:project", PROJECT_NAME, "--default", "-g", "com.my-company");
      assertThat(result.stderr()).contains("'com.my-company' is not a valid Maven group id");
    }
  }

  @Nested
  class ParseConfig {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private Prompter prompter(String answers) {
      ColorSupport.setLevel(0);
      return new Prompter(
          new BufferedReader(new StringReader(answers)), new PrintStream(output, true, StandardCharsets.UTF_8), null);
    }

    private Project.ProjectFlags flags(boolean defaults, String providerPackageName) {
      return new Project.ProjectFlags(
          PROJECT_NAME, "ignored", "9.9.9", null, null, "GPL", null, providerPackageName, null, defaults, true, false);
    }

    @Test
    void defaultIgnoresTheOtherValueFlags() {
      ProjectInitializerConfig config = Project.parseConfig(prompter(""), flags(true, "x.y"), "0.5.1");
      assertThat(config)
          .isEqualTo(
              new ProjectInitializerConfig(
                  PROJECT_NAME, "", "0.1.0", "", "", "MIT", "", FrameworkApi.AZURE_PROVIDER_PACKAGE, "0.5.1", true, true,
                  false, "com.example"));
      assertThat(output.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void asksOnlyTheMissingValuesAndAppliesDefaults() {
      ProjectInitializerConfig config = Project.parseConfig(prompter("\n\n\n\n2\n"), flags(false, null), "4.1.0");
      assertThat(config.description()).isEqualTo("ignored");
      assertThat(config.version()).isEqualTo("9.9.9");
      assertThat(config.license()).isEqualTo("GPL");
      assertThat(config.author()).isEmpty();
      assertThat(config.groupId()).isEqualTo("com.example");
      assertThat(config.providerPackageName()).isEqualTo(FrameworkApi.LOCAL_PROVIDER_PACKAGE);
      assertThat(config.defaults()).isFalse();
      assertThat(config.skipInstall()).isTrue();
    }

    @Test
    void otherProviderAsksForThePackage() {
      ProjectInitializerConfig config =
          Project.parseConfig(prompter("\n\n\n\n3\norg.acme.provider\n"), flags(false, null), "4.1.0");
      assertThat(config.providerPackageName()).isEqualTo("org.acme.provider");
      assertThat(output.toString(StandardCharsets.UTF_8))
          .contains("What's the other provider integration library? e.g. com.boostercloud.framework.provider.azure");
      assertThat(config.hasProductionEnvironment()).isTrue();
    }

    @Test
    void emptyOtherProviderMeansLocalOnly() {
      ProjectInitializerConfig config = Project.parseConfig(prompter("\n\n\n\n3\n\n"), flags(false, null), "4.1.0");
      assertThat(config.providerPackageName()).isEmpty();
      assertThat(config.hasProductionEnvironment()).isFalse();
    }

    @Test
    void normalizesProviderPackages() {
      assertThat(Project.normalizeProviderPackage("@boostercloud/framework-provider-azure"))
          .isEqualTo(FrameworkApi.AZURE_PROVIDER_PACKAGE);
      assertThat(Project.normalizeProviderPackage("@boostercloud/framework-provider-local"))
          .isEqualTo(FrameworkApi.LOCAL_PROVIDER_PACKAGE);
      assertThat(Project.normalizeProviderPackage(" org.acme ")).isEqualTo("org.acme");
      assertThatThrownBy(() -> Project.normalizeProviderPackage("not a package"))
          .hasMessageContaining("'not a package' is not a valid provider package");
    }
  }
}
