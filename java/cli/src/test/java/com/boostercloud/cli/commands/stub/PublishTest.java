package com.boostercloud.cli.commands.stub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.boostercloud.cli.services.StubPublisher;
import com.boostercloud.cli.services.Templates;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Port of {@code test/commands/stub/publish.test.ts}. */
class PublishTest {
  @TempDir Path dir;

  private TestCli cli;

  @BeforeEach
  void setUp() {
    TestProjects.create(dir, "4.1.0");
    cli = new TestCli(dir);
  }

  private void assertStubsPublished() {
    for (String name : StubPublisher.resourceTemplateFileNames()) {
      assertThat(TestProjects.read(dir.resolve("stubs").resolve(name))).isEqualTo(Templates.bundled(name));
    }
  }

  @Test
  void initCallsCheckCurrentDirBoosterVersion() {
    assertThatCode(() -> new Publish(cli.context()).init()).doesNotThrowAnyException();
  }

  @Nested
  class PublishesStubFilesCorrectly {
    @Test
    void whenTheStubsFolderDoesNotYetExist() {
      TestCli.Result result = cli.run("stub:publish");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout())
          .doesNotContain("Do you want to overwrite it?")
          .contains("ℹ boost stub:publish 🗄")
          .contains("✔ Publishing stubs")
          .contains("ℹ Resource template stubs published!");
      assertStubsPublished();
    }

    @Test
    void whenTheStubsFolderExistsAndTheUserAgreesToOverwriteIt() {
      TestProjects.write(dir.resolve("stubs/command.stub"), "old");
      TestCli.Result result = cli.stdin("y\n").run("stub:publish");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout()).contains("Stubs folder already exists. Do you want to overwrite it?");
      assertStubsPublished();
    }

    @Test
    void whenTheStubsFolderExistsAndForceIsUsed() {
      TestProjects.write(dir.resolve("stubs/command.stub"), "old");
      TestCli.Result result = cli.run("stub:publish", "--force");
      assertThat(result.exitCode()).isZero();
      assertThat(result.stdout()).doesNotContain("Do you want to overwrite it?");
      assertStubsPublished();
    }

    @Test
    void withTheShortForceFlag() {
      TestProjects.write(dir.resolve("stubs/command.stub"), "old");
      assertThat(cli.run("stub:publish", "-f").exitCode()).isZero();
      assertStubsPublished();
    }
  }

  @Nested
  class DisplaysAnError {
    @Test
    void whenTheUserRefusesToOverwriteTheStubsFolder() {
      TestProjects.write(dir.resolve("stubs/command.stub"), "old");
      TestCli.Result result = cli.stdin("n\n").run("stub:publish");
      assertThat(result.exitCode()).isEqualTo(1);
      assertThat(result.stderr()).contains("Stubs folder already exists. Use --force option to overwrite files in it");
      assertThat(TestProjects.read(dir.resolve("stubs/command.stub"))).isEqualTo("old");
    }

    @Test
    void outsideABoosterProject() {
      Path outside = dir.resolve("outside");
      TestProjects.write(outside.resolve("readme.txt"), "x");
      TestCli.Result result = new TestCli(outside).run("stub:publish");
      assertThat(result.exitCode()).isEqualTo(1);
      assertThat(result.stderr()).contains("There was an error when recognizing the application");
    }

    @Test
    void withANonexistingFlag() {
      TestCli.Result result = cli.run("stub:publish", "--nonexistingoption");
      assertThat(result.exitCode()).isEqualTo(2);
      assertThat(result.stderr()).contains("Nonexistent flag: --nonexistingoption");
      assertThat(dir.resolve("stubs")).doesNotExist();
    }
  }
}
