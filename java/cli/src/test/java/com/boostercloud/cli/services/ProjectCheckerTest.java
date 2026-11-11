package com.boostercloud.cli.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.CliContext;
import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Port of {@code test/services/project-checker.test.ts}. The fixtures ({@code mock_project} with Booster 1.11.2 and
 * {@code mock_project_bad_index}) are created in a temporary directory.
 */
class ProjectCheckerTest {
  @TempDir Path dir;

  private Path mockProject;
  private Path mockProjectBadIndex;

  @BeforeEach
  void createFixtures() {
    mockProject = TestProjects.create(dir.resolve("mock_project"), "1.11.2");
    mockProjectBadIndex = TestProjects.create(dir.resolve("mock_project_bad_index"), "1.11.2");
    TestProjects.write(
        TestProjects.source(mockProjectBadIndex, "Main"),
        "package com.example.mock;\n\npublic class Main {\n  public static void main(String[] args) {}\n}\n");
  }

  @Nested
  class CheckCurrentDirIsABoosterProject {
    @Test
    void isABoosterProject() {
      assertThatCode(() -> ProjectChecker.checkCurrentDirIsABoosterProject(new TestCli(mockProject).context()))
          .doesNotThrowAnyException();
    }

    @Test
    void isABoosterProjectWithBadMainClass() {
      assertThatThrownBy(() -> ProjectChecker.checkCurrentDirIsABoosterProject(new TestCli(mockProjectBadIndex).context()))
          .isInstanceOf(CliException.class)
          .hasMessage(
              "There was an error when recognizing the application. Make sure you are in the root path of a Booster"
                  + " project:\nThe main application file does not start a Booster application. Verify you are in the"
                  + " right project");
    }

    @Test
    void isNotABoosterProject() {
      assertThatThrownBy(() -> ProjectChecker.checkCurrentDirIsABoosterProject(new TestCli(dir).context()))
          .hasMessageStartingWith(
              "There was an error when recognizing the application. Make sure you are in the root path of a Booster"
                  + " project:\nCannot find the file");
    }
  }

  @Nested
  class CheckItIsABoosterProject {
    @Test
    void isABoosterProject() {
      assertThatCode(() -> ProjectChecker.checkItIsABoosterProject(mockProject)).doesNotThrowAnyException();
    }

    @Test
    void isABoosterProjectWithBadMainClass() {
      assertThatThrownBy(() -> ProjectChecker.checkItIsABoosterProject(mockProjectBadIndex))
          .hasMessageContaining("The main application file does not start a Booster application");
    }

    @Test
    void isNotABoosterProject() {
      assertThatThrownBy(() -> ProjectChecker.checkItIsABoosterProject(dir))
          .hasMessageContaining("There was an error when recognizing the application");
    }

    @Test
    void requiresTheFrameworkCoreDependency() {
      TestProjects.write(
          mockProject.resolve("pom.xml"),
          TestProjects.pom("1.11.2").replace("booster-framework-core", "something-else"));
      assertThatThrownBy(() -> ProjectChecker.checkItIsABoosterProject(mockProject))
          .hasMessageEndingWith("The pom.xml file does not depend on com.boostercloud:booster-framework-core");
    }

    @Test
    void requiresTheMainClassProperty() {
      TestProjects.write(
          mockProject.resolve("pom.xml"),
          TestProjects.pom("1.11.2").replace("<booster.mainClass>com.example.mock.Main</booster.mainClass>", ""));
      assertThatThrownBy(() -> ProjectChecker.checkItIsABoosterProject(mockProject))
          .hasMessageEndingWith("The pom.xml file does not define the 'booster.mainClass' property with the main class of the application");
    }

    @Test
    void requiresTheMainSourceFile() throws Exception {
      Files.delete(TestProjects.source(mockProject, "Main"));
      assertThatThrownBy(() -> ProjectChecker.checkItIsABoosterProject(mockProject))
          .hasMessageContaining("Cannot find the main application file");
    }

    @Test
    void rejectsInvalidPoms() {
      TestProjects.write(mockProject.resolve("pom.xml"), "<project><unclosed></project>");
      assertThatThrownBy(() -> ProjectChecker.checkItIsABoosterProject(mockProject))
          .hasMessageContaining("is not a valid XML file");
    }
  }

  @Nested
  class CheckProjectAlreadyExists {
    @Test
    void shouldDoNothingIfProjectWithGivenNameDoesNotExist() {
      TestCli cli = new TestCli(dir);
      assertThatCode(() -> ProjectChecker.checkProjectAlreadyExists(cli.context(), "mock_project_test"))
          .doesNotThrowAnyException();
      assertThat(cli.stdout()).doesNotContain("already exists");
    }

    @Test
    void shouldThrowWhenProjectExistsAndUserRefusesToOverwriteIt() {
      TestCli cli = new TestCli(dir).stdin("n\n");
      assertThatThrownBy(() -> ProjectChecker.checkProjectAlreadyExists(cli.context(), "mock_project"))
          .hasMessage("The folder you're trying to use already exists. Please use another project name");
      assertThat(cli.stdout()).contains("Project folder \"mock_project\" already exists. Do you want to overwrite it?");
      assertThat(mockProject).exists();
    }

    @Test
    void shouldRemoveProjectFolderWhenUserAgreesToOverwriteIt() {
      TestCli cli = new TestCli(dir).stdin("y\n");
      ProjectChecker.checkProjectAlreadyExists(cli.context(), "mock_project");
      assertThat(mockProject).doesNotExist();
    }
  }

  @Nested
  class CheckResourceExists {
    private final String placementDir = "src/main/java/com/example/mock/entities";

    @Test
    void shouldPrintInfoMessageAndDoNothingIfResourceDoesNotExist() {
      TestCli cli = new TestCli(mockProject);
      ProjectChecker.checkResourceExists(cli.context(), "TestResource", placementDir, ".java");
      assertThat(cli.stdout()).isEqualTo("ℹ Checking if resource already exists..." + System.lineSeparator());
    }

    @Test
    void shouldThrowWhenResourceExistsAndUserRefusesToOverwriteIt() {
      TestProjects.write(mockProject.resolve(placementDir).resolve("TestResource.java"), "class TestResource {}");
      TestCli cli = new TestCli(mockProject).stdin("n\n");
      assertThatThrownBy(() -> ProjectChecker.checkResourceExists(cli.context(), "TestResource", placementDir, ".java"))
          .hasMessage("The 'entities' resource \"TestResource.java\" already exists. Please use another resource name");
      assertThat(cli.stdout()).contains("Resource: \"TestResource.java\" already exists. Do you want to overwrite it?");
      assertThat(mockProject.resolve(placementDir).resolve("TestResource.java")).exists();
    }

    @Test
    void shouldRemoveResourceWhenItExistsAndUserAgreesToOverwriteIt() {
      Path resource = mockProject.resolve(placementDir).resolve("TestResource.java");
      TestProjects.write(resource, "class TestResource {}");
      CliContext ctx = new TestCli(mockProject).stdin("yes\n").context();
      ProjectChecker.checkResourceExists(ctx, "TestResource", placementDir, ".java");
      assertThat(resource).doesNotExist();
    }
  }

  @Nested
  class CheckCurrentDirBoosterVersion {
    // the project version of the fixture is 1.11.2
    private void check(String cliVersion) {
      CliContext ctx = new TestCli(mockProject).context();
      ProjectChecker.checkCurrentDirBoosterVersion(ctx, cliVersion);
    }

    @Test
    void versionsMatch() {
      TestCli cli = new TestCli(mockProject);
      ProjectChecker.checkCurrentDirBoosterVersion(cli.context(), "1.11.2");
      assertThat(cli.stdout()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"1.11.3", "1.11.0"})
    void versionsDifferInFixNumber(String cliVersion) {
      TestCli cli = new TestCli(mockProject);
      ProjectChecker.checkCurrentDirBoosterVersion(cli.context(), cliVersion);
      assertThat(cli.stdout())
          .contains(
              "ℹ WARNING: Project Booster version differs in the 'fix' section. CLI version: "
                  + cliVersion
                  + ". Project Booster version: 1.11.2");
    }

    @Test
    void cliLowerThanProjectVersionInFeatureSection() {
      assertThatThrownBy(() -> check("1.10.2"))
          .isInstanceOf(ProjectChecker.LowerCliVersionError.class)
          .hasMessage(
              "CLI version 1.10.2 is lower than your project Booster version 1.11.2. Please upgrade your Booster CLI"
                  + " (com.boostercloud:booster-cli) to the same version: 1.11.2")
          .satisfies(e -> assertThat(((ProjectChecker.LowerCliVersionError) e).section()).isEqualTo("feature"));
    }

    @Test
    void cliLowerThanProjectVersionInBreakingSection() {
      assertThatThrownBy(() -> check("0.11.2"))
          .isInstanceOf(ProjectChecker.LowerCliVersionError.class)
          .hasMessageContaining("Please upgrade your Booster CLI")
          .satisfies(e -> assertThat(((ProjectChecker.LowerCliVersionError) e).section()).isEqualTo("breaking"));
    }

    @Test
    void cliHigherThanProjectVersionInFeatureSection() {
      assertThatThrownBy(() -> check("1.12.2"))
          .isInstanceOf(ProjectChecker.HigherCliVersionError.class)
          .hasMessage(
              "CLI version 1.12.2 is higher than your project Booster version 1.11.2 in the 'feature' section. Please"
                  + " upgrade your project Booster dependencies.");
    }

    @Test
    void cliHigherThanProjectVersionInBreakingSection() {
      assertThatThrownBy(() -> check("2.11.2"))
          .isInstanceOf(ProjectChecker.HigherCliVersionError.class)
          .hasMessageContaining("in the 'breaking' section. Please upgrade your project Booster dependencies");
    }

    @Test
    void cliVersionWrongLengthShorter() {
      assertThatThrownBy(() -> check("1.11")).hasMessageContaining("Versions must follow semantic convention X.Y.Z");
    }

    @Test
    void cliVersionWrongLengthLonger() {
      assertThatThrownBy(() -> check("1.11.2.1")).hasMessageContaining("Versions must follow semantic convention X.Y.Z");
    }

    @Test
    void outsideABoosterProject() {
      TestCli cli = new TestCli(dir);
      assertThatThrownBy(() -> ProjectChecker.checkCurrentDirBoosterVersion(cli.context(), "1.11.2"))
          .hasMessageContaining("There was an error when recognizing the application");
      assertThat(cli.stdout()).isEmpty();
    }

    @Test
    void readsLiteralAndManagedVersions() {
      TestProjects.write(mockProject.resolve("pom.xml"), TestProjects.pom("1.11.2").replace("${booster.version}", "1.11.2-SNAPSHOT"));
      assertThat(ProjectChecker.getBoosterVersion(mockProject)).isEqualTo("1.11.2");
      String managed =
          """
          <project>
            <artifactId>x</artifactId>
            <dependencyManagement><dependencies><dependency>
              <groupId>com.boostercloud</groupId><artifactId>booster-framework-core</artifactId><version>2.0.1</version>
            </dependency></dependencies></dependencyManagement>
            <dependencies><dependency>
              <groupId>com.boostercloud</groupId><artifactId>booster-framework-core</artifactId>
            </dependency></dependencies>
          </project>
          """;
      TestProjects.write(mockProject.resolve("pom.xml"), managed);
      assertThat(ProjectChecker.getBoosterVersion(mockProject)).isEqualTo("2.0.1");
    }

    @Test
    void failsWhenTheVersionIsInheritedFromAnUnknownParent() {
      TestProjects.write(
          mockProject.resolve("pom.xml"), TestProjects.pom("1.11.2").replace("${booster.version}", "${from.parent}"));
      assertThatThrownBy(() -> ProjectChecker.getBoosterVersion(mockProject))
          .hasMessageContaining("Cannot determine the version of booster-framework-core");
    }

    @Test
    void normalizesMavenVersions() {
      assertThat(ProjectChecker.normalizeVersion("4.1.0")).isEqualTo("4.1.0");
      assertThat(ProjectChecker.normalizeVersion("4.1.0-SNAPSHOT")).isEqualTo("4.1.0");
      assertThat(ProjectChecker.normalizeVersion("[4.1.0,5.0.0)")).isEqualTo("4.1.0");
      assertThat(ProjectChecker.normalizeVersion("[4.1.0]")).isEqualTo("4.1.0");
    }
  }
}
