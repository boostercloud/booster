package com.boostercloud.rocket.fileuploads.local.handlers;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.local.LocalStorage;
import com.boostercloud.rocket.fileuploads.types.NodePath;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Java addition (no TypeScript counterpart for the local provider): deletes a file of a storage.
 */
public final class DeleteFile {

  private DeleteFile() {}

  /**
   * Deletes {@code <project>/<storage>/<container>/<directory>/<fileName>}. The local file watcher then notifies the
   * deletion like TypeScript notifies any change of the folder (an {@code UploadedFileEvent} is stored for it).
   *
   * @param config the application configuration (for the project directory)
   * @param rocketFilesUserConfiguration the storage
   * @param directory the directory
   * @param fileName the file name
   * @return {@code true} when the file was deleted, {@code false} when it did not exist
   * @throws IllegalArgumentException {@code Invalid file name <fileName>} when the path leaves the container
   * @throws UncheckedIOException {@code EISDIR: illegal operation on a directory, unlink '<path>'} for a directory,
   *     or the I/O failure
   */
  public static boolean deleteFile(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      String directory, String fileName) {
    Path container = LocalStorage.containerDirectory(config, rocketFilesUserConfiguration);
    Path file = Path.of(NodePath.join(container.toString(), directory, fileName));
    if (!file.normalize().startsWith(container.normalize()) || file.normalize().equals(container.normalize())) {
      throw new IllegalArgumentException("Invalid file name " + fileName);
    }
    if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
      throw new UncheckedIOException("EISDIR: illegal operation on a directory, unlink '" + file + "'",
          new IOException("Is a directory: " + file));
    }
    try {
      return Files.deleteIfExists(file);
    } catch (IOException e) {
      throw LocalStorage.nodeError(e, "unlink", file);
    }
  }
}
