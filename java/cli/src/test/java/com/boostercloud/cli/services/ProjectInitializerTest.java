package com.boostercloud.cli.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.CliVersion;
import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.services.project.PomFile;
import com.boostercloud.cli.services.project.ProjectLayout;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Port of {@code test/services/project-initializer.test.ts}. */
class ProjectInitializerTest {
  @TempDir Path dir;

  private static ProjectInitializerConfig config(String provider) {
    return new ProjectInitializerConfig(
        "test-project", "", "0.1.0", "", "", "MIT", "", provider, "0.5.1", true, false, false, "com.example");
  }

  private final ProjectInitializerConfig defaultConfig = config(FrameworkApi.AZURE_PROVIDER_PACKAGE);

  @Test
  void initializesGit() {
    TestCli cli = new TestCli(dir);
    ProjectInitializer.initializeGit(cli.context(), defaultConfig);
    assertThat(cli.process().lines()).containsExactly("git init", "git add -A", "git commit -m Initial commit");
    assertThat(cli.process().calls().get(2).command()).containsExactly("git", "commit", "-m", "Initial commit");
    assertThat(cli.process().calls()).allSatisfy(c -> assertThat(c.cwd()).isEqualTo(dir.resolve("test-project")));
  }

  @Test
  void reportsGitFailuresWithTheirOutput() {
    TestCli cli = new TestCli(dir);
    cli.process().failWhen(cmd -> cmd.contains("commit"), 128, "Please tell me who you are");
    assertThatThrownBy(() -> ProjectInitializer.initializeGit(cli.context(), defaultConfig))
        .isInstanceOf(CliException.class)
        .hasMessage("Could not initialize git repository\nPlease tell me who you are");
  }

  @Test
  void generatesTheRootDirectory() {
    ProjectInitializer.generateRootDirectory(new TestCli(dir).context(), defaultConfig);
    Path base = dir.resolve("test-project/src/main/java/com/example/testproject");
    for (String pkg :
        new String[] {
          "commands", "events", "entities", "readmodels", "config", "common", "eventhandlers", "scheduledcommands",
          "queries"
        }) {
      assertThat(base.resolve(pkg)).isDirectory();
    }
    assertThat(dir.resolve("test-project/src/main/resources")).isDirectory();
  }

  @Test
  void installsDependencies() {
    TestCli cli = new TestCli(dir);
    ProjectInitializer.installDependencies(cli.context(), defaultConfig);
    assertThat(cli.process().lines()).containsExactly("mvn -B -q dependency:resolve");
    assertThat(cli.process().calls().getFirst().cwd()).isEqualTo(dir.resolve("test-project"));
  }

  @Test
  void reportsDependencyFailures() {
    TestCli cli = new TestCli(dir);
    cli.process().failWhen(cmd -> true, 1, "Could not resolve dependencies");
    assertThatThrownBy(() -> ProjectInitializer.installDependencies(cli.context(), defaultConfig))
        .hasMessageStartingWith("[Error] Could not install dependencies:")
        .hasMessageContaining("Could not resolve dependencies");
  }

  @Nested
  class GenerateConfigFiles {
    @Test
    void writesEveryFile() {
      ProjectInitializer.generateConfigFiles(new TestCli(dir).context(), defaultConfig);
      Path project = dir.resolve("test-project");
      assertThat(project.resolve("pom.xml")).isRegularFile();
      assertThat(project.resolve(".gitignore")).isRegularFile();
      assertThat(project.resolve("README.md")).isRegularFile();
      assertThat(project.resolve("src/main/java/com/example/testproject/Main.java")).isRegularFile();
      assertThat(project.resolve("src/main/java/com/example/testproject/config/Config.java")).isRegularFile();
    }

    @Test
    void theGeneratedPomIsABoosterProject() {
      ProjectInitializer.generateConfigFiles(new TestCli(dir).context(), defaultConfig);
      Path project = dir.resolve("test-project");
      PomFile pom = PomFile.read(project.resolve("pom.xml"));
      assertThat(pom.groupId()).isEqualTo("com.example");
      assertThat(pom.artifactId()).isEqualTo("test-project");
      assertThat(pom.version()).isEqualTo("0.1.0");
      assertThat(pom.property("booster.mainClass")).contains("com.example.testproject.Main");
      assertThat(pom.dependencies())
          .extracting(PomFile.Dependency::artifactId)
          .containsExactly(
              "booster-framework-core",
              "booster-framework-provider-local",
              "booster-framework-provider-local-infrastructure",
              "booster-framework-provider-azure",
              "booster-framework-provider-azure-infrastructure",
              "junit-jupiter");
      assertThat(pom.dependency("com.boostercloud", "booster-framework-core").orElseThrow().version())
          .isEqualTo(CliVersion.VERSION);
      assertThat(ProjectLayout.of(project).mainSourceFile()).isRegularFile();
      ProjectChecker.checkItIsABoosterProject(project);
    }

    @Test
    void localOnlyProjectsHaveNoAzureDependenciesNorProductionEnvironment() {
      ProjectInitializer.generateConfigFiles(new TestCli(dir).context(), config(""));
      Path project = dir.resolve("test-project");
      assertThat(TestProjects.read(project.resolve("pom.xml"))).doesNotContain("azure");
      assertThat(TestProjects.read(project.resolve("src/main/java/com/example/testproject/config/Config.java")))
          .contains("Booster.configure(\"local\"")
          .doesNotContain("production");
    }

    @Test
    void customProvidersGetAProductionEnvironmentAndAHint() {
      ProjectInitializer.generateConfigFiles(new TestCli(dir).context(), config("org.acme.provider"));
      Path project = dir.resolve("test-project");
      assertThat(TestProjects.read(project.resolve("pom.xml")))
          .doesNotContain("booster-framework-provider-azure")
          .contains("Add here the dependencies that provide the 'org.acme.provider' provider package");
      assertThat(TestProjects.read(project.resolve("src/main/java/com/example/testproject/config/Config.java")))
          .contains("config.providerPackage = \"org.acme.provider\";");
    }

    @Test
    void escapesValuesForXml() {
      ProjectInitializerConfig withSpecialCharacters =
          new ProjectInitializerConfig(
              "test-project", "Cats & \"dogs\" <3", "1.0.0", "Jane O'Hara", "https://a.b/?x=1", "Apache-2.0",
              "git@github.com:a/b.git", "", "4.1.0", false, false, false, "org.acme");
      ProjectInitializer.generateConfigFiles(new TestCli(dir).context(), withSpecialCharacters);
      Path project = dir.resolve("test-project");
      PomFile pom = PomFile.read(project.resolve("pom.xml"));
      String text = TestProjects.read(project.resolve("pom.xml"));
      assertThat(text).contains("<description>Cats &amp; &quot;dogs&quot; &lt;3</description>");
      assertThat(text).contains("<name>Jane O&#39;Hara</name>");
      assertThat(pom.groupId()).isEqualTo("org.acme");
      assertThat(TestProjects.read(project.resolve("README.md"))).contains("Cats & \"dogs\" <3");
    }

    /**
     * In Java, packages are directories: an unanchored {@code target/}, {@code tmp/} or {@code coverage} would
     * leave packages with those names (or the whole tree, with the group {@code com.target}) out of git, initial
     * commit included. Checked with the real git when it is installed.
     */
    @Test
    void theGitignoreOnlyIgnoresTheRootBuildDirectories() throws Exception {
      Assumptions.assumeTrue(gitIsInstalled(), "git is not installed");
      ProjectInitializerConfig targetGroup =
          new ProjectInitializerConfig(
              "test-project", "", "0.1.0", "", "", "MIT", "", "", "4.1.0", true, false, false, "com.target");
      ProjectInitializer.generateConfigFiles(new TestCli(dir).context(), targetGroup);
      Path project = dir.resolve("test-project");
      String base = "src/main/java/com/target/testproject/";
      List<String> kept =
          List.of(
              base + "Main.java",
              base + "target/Target.java",
              base + "tmp/Scratch.java",
              base + "coverage/Report.java",
              "src/test/java/com/target/testproject/target/TargetTest.java");
      List<String> ignored =
          List.of("target/classes/Main.class", "tmp/scratch.txt", "coverage/index.html", "errors.log", ".booster/data.json");
      for (String file : kept) {
        TestProjects.write(project.resolve(file), "");
      }
      for (String file : ignored) {
        TestProjects.write(project.resolve(file), "");
      }
      assertThat(git(project, "init", "-q")).isZero();

      assertThat(kept).allSatisfy(file -> assertThat(git(project, "check-ignore", "-q", file)).as(file).isEqualTo(1));
      assertThat(ignored).allSatisfy(file -> assertThat(git(project, "check-ignore", "-q", file)).as(file).isZero());
    }
  }

  private static boolean gitIsInstalled() {
    try {
      return new ProcessBuilder("git", "--version").redirectErrorStream(true).start().waitFor() == 0;
    } catch (java.io.IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /** Runs git without the machine's configuration (and so without its global excludes file). */
  private int git(Path directory, String... arguments) {
    List<String> command = new java.util.ArrayList<>(List.of("git", "-c", "core.excludesFile=" + dir.resolve("no-excludes")));
    command.addAll(List.of(arguments));
    ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
    builder.environment().put("GIT_CONFIG_GLOBAL", dir.resolve("no-gitconfig").toString());
    builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
    try {
      Process process = builder.start();
      process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
      return process.waitFor();
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  @Test
  void templateViewDescribesTheProvider() {
    Map<String, Object> azure = ProjectInitializer.templateView(defaultConfig);
    assertThat(azure)
        .containsEntry("basePackage", "com.example.testproject")
        .containsEntry("productionProvider", true)
        .containsEntry("azure", true)
        .containsEntry("customProvider", false)
        .containsEntry("boosterVersion", CliVersion.VERSION);
    assertThat(ProjectInitializer.templateView(config(FrameworkApi.LOCAL_PROVIDER_PACKAGE)))
        .containsEntry("productionProvider", false)
        .containsEntry("azure", false);
  }

  @Test
  void basePackageIsDerivedFromTheProjectName() {
    assertThat(config("").basePackage()).isEqualTo("com.example.testproject");
    assertThat(new ProjectInitializerConfig("3d-shop", "", "", "", "", "", "", "", "", false, false, false, "org.x").basePackage())
        .isEqualTo("org.x.app3dshop");
  }
}
