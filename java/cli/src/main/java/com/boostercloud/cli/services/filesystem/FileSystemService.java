package com.boostercloud.cli.services.filesystem;

import java.nio.file.Path;
import java.util.List;

/**
 * File-system operations needed by the package-manager inference ({@code services/file-system/index.ts}). The
 * TypeScript effect service becomes a plain interface whose failures are {@link FileSystemError}s.
 */
public interface FileSystemService {
  /**
   * Lists the names of the entries of a directory.
   *
   * @param directoryPath the directory
   * @return the entry names
   * @throws FileSystemError when the directory cannot be read
   */
  List<String> readDirectoryContents(Path directoryPath);

  /**
   * Reads a UTF-8 text file.
   *
   * @param filePath the file
   * @return its contents
   * @throws FileSystemError when the file cannot be read
   */
  String readFileContents(Path filePath);
}
