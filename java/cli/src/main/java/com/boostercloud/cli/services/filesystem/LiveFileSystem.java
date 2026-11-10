package com.boostercloud.cli.services.filesystem;

import com.boostercloud.cli.common.CliException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** {@link FileSystemService} backed by {@code java.nio.file} ({@code services/file-system/live.impl.ts}). */
public final class LiveFileSystem implements FileSystemService {
  @Override
  public List<String> readDirectoryContents(Path directoryPath) {
    try (Stream<Path> entries = Files.list(directoryPath)) {
      return entries.map(p -> p.getFileName().toString()).sorted().toList();
    } catch (IOException | RuntimeException e) {
      throw new FileSystemError(
          new CliException("There were some issues reading the directory " + directoryPath + ": " + e, e));
    }
  }

  @Override
  public String readFileContents(Path filePath) {
    try {
      return Files.readString(filePath, StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException e) {
      throw new FileSystemError(new CliException("There were some issues reading the file " + filePath + ": " + e, e));
    }
  }
}
