package com.boostercloud.cli.services.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Port of {@code test/services/file-system/live.test.ts}. */
class LiveFileSystemTest {
  @TempDir Path dir;

  private final LiveFileSystem fileSystem = new LiveFileSystem();

  @Test
  void readsDirectoryContents() throws Exception {
    Files.writeString(dir.resolve("b.txt"), "b");
    Files.createDirectory(dir.resolve("a"));
    assertThat(fileSystem.readDirectoryContents(dir)).containsExactly("a", "b.txt");
  }

  @Test
  void readsFileContents() throws Exception {
    Files.writeString(dir.resolve("file.txt"), "contents ✓");
    assertThat(fileSystem.readFileContents(dir.resolve("file.txt"))).isEqualTo("contents ✓");
  }

  @Test
  void wrapsDirectoryErrors() {
    Path missing = dir.resolve("missing");
    assertThatThrownBy(() -> fileSystem.readDirectoryContents(missing))
        .isInstanceOf(FileSystemError.class)
        .hasMessageStartingWith("There were some issues reading the directory " + missing + ": ");
  }

  @Test
  void wrapsFileErrors() {
    Path missing = dir.resolve("missing.txt");
    assertThatThrownBy(() -> fileSystem.readFileContents(missing))
        .isInstanceOf(FileSystemError.class)
        .hasMessageStartingWith("There were some issues reading the file " + missing + ": ")
        .satisfies(e -> assertThat(((FileSystemError) e).error()).isNotNull());
  }
}
