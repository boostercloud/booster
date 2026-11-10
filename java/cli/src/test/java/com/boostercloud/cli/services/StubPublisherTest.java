package com.boostercloud.cli.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.testing.TestProjects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Port of {@code test/services/stub-publisher.test.ts}. */
class StubPublisherTest {
  @TempDir Path dir;

  @Test
  void resourceStubFilePath() {
    assertThat(StubPublisher.resourceStubFilePath(dir, "command.stub")).isEqualTo(dir.resolve("stubs/command.stub"));
  }

  @Test
  void resourceTemplateFilePath() {
    assertThat(StubPublisher.resourceTemplateFilePath("command.stub"))
        .isEqualTo("com/boostercloud/cli/templates/command.stub");
  }

  @Nested
  class CheckStubsFolderExists {
    @Test
    void trueWhenTheFolderExists() throws Exception {
      Files.createDirectory(dir.resolve("stubs"));
      assertThat(StubPublisher.checkStubsFolderExists(dir)).isTrue();
    }

    @Test
    void falseWhenTheFolderDoesNotExist() {
      assertThat(StubPublisher.checkStubsFolderExists(dir)).isFalse();
    }
  }

  @Nested
  class CheckResourceStubFileExists {
    @Test
    void trueWhenTheFileExists() {
      TestProjects.write(dir.resolve("stubs/command.stub"), "x");
      assertThat(StubPublisher.checkResourceStubFileExists(dir.resolve("stubs/command.stub"))).isTrue();
    }

    @Test
    void falseWhenTheFileDoesNotExist() {
      assertThat(StubPublisher.checkResourceStubFileExists(dir.resolve("stubs/command.stub"))).isFalse();
    }
  }

  @Test
  void createsTheStubsFolder() {
    StubPublisher.createStubsFolder(dir);
    assertThat(dir.resolve("stubs")).isDirectory();
    assertThatThrownBy(() -> StubPublisher.createStubsFolder(dir)).isInstanceOf(CliException.class);
  }

  @Nested
  class CreateTemplateFileMap {
    @Test
    void keepsOnlyStubFiles() {
      Map<String, Path> map =
          StubPublisher.createTemplateFileMap(dir, List.of("fake-command.stub", "fake-event.stub", "fake-directory", "readme.md"));
      assertThat(map)
          .containsExactly(
              Map.entry("com/boostercloud/cli/templates/fake-command.stub", dir.resolve("stubs/fake-command.stub")),
              Map.entry("com/boostercloud/cli/templates/fake-event.stub", dir.resolve("stubs/fake-event.stub")));
    }

    @Test
    void whenThereAreNoFiles() {
      assertThat(StubPublisher.createTemplateFileMap(dir, List.of())).isEmpty();
    }
  }

  @Test
  void listsEveryBuiltInTemplate() {
    assertThat(StubPublisher.resourceTemplateFileNames())
        .containsExactly(
            "command.stub",
            "query.stub",
            "entity.stub",
            "event.stub",
            "event-handler.stub",
            "read-model.stub",
            "scheduled-command.stub",
            "type.stub");
  }

  @Nested
  class PublishStubFiles {
    @Test
    void copiesTemplateFiles() {
      StubPublisher.createStubsFolder(dir);
      StubPublisher.publishStubFiles(dir);
      for (String name : StubPublisher.resourceTemplateFileNames()) {
        assertThat(TestProjects.read(dir.resolve("stubs").resolve(name))).isEqualTo(Templates.bundled(name));
      }
    }

    @Test
    void overwritesExistingFiles() {
      TestProjects.write(dir.resolve("stubs/command.stub"), "old");
      StubPublisher.publishStubFiles(dir);
      assertThat(TestProjects.read(dir.resolve("stubs/command.stub"))).isEqualTo(Templates.bundled("command.stub"));
    }

    @Test
    void throwsIfAStubFileCannotBeCopied() {
      assertThatThrownBy(() -> StubPublisher.publishStubFiles(dir))
          .isInstanceOf(CliException.class)
          .hasMessageStartingWith("Error when publishing stubs");
    }
  }
}
