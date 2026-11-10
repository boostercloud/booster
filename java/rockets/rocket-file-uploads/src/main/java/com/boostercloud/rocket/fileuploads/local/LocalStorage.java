package com.boostercloud.rocket.fileuploads.local;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.types.NodePath;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

/**
 * Java-only: where the local provider keeps the files, the replacement of TypeScript's {@code process.cwd()}.
 *
 * <p>Files live in {@code <project>/<storageName>/<containerName>/<directory>/<fileName>}, where {@code <project>} is
 * {@code config.userProjectRootPath()} (the project directory, set by {@code Booster.start}) or, before the application
 * started, the working directory. {@code boost start} runs the application from the project directory, so this is the
 * folder TypeScript uses and the one holding {@code .booster}.
 */
public final class LocalStorage {

  private LocalStorage() {}

  /**
   * @param config the application configuration
   * @return the project directory
   */
  public static Path projectDirectory(BoosterConfig config) {
    String root = null;
    if (config != null) {
      try {
        root = config.userProjectRootPath();
      } catch (IllegalStateException notStarted) {
        root = null;
      }
    }
    return Path.of(root != null ? root : System.getProperty("user.dir")).toAbsolutePath();
  }

  /**
   * @param projectDirectory the project directory
   * @param storageName the storage
   * @param containerName the container
   * @return {@code path.join(projectDirectory, storageName, containerName)}
   */
  public static Path containerDirectory(Path projectDirectory, String storageName, String containerName) {
    return Path.of(NodePath.join(projectDirectory.toString(), storageName, containerName));
  }

  /**
   * @param config the application configuration
   * @param userConfiguration the storage
   * @return the container directory of the storage
   */
  public static Path containerDirectory(BoosterConfig config, RocketFilesUserConfiguration userConfiguration) {
    return containerDirectory(projectDirectory(config), userConfiguration.storageName(),
        userConfiguration.containerName());
  }

  /**
   * An I/O failure with Node's message ({@code ENOENT: no such file or directory, scandir '<path>'}).
   *
   * @param e the failure
   * @param syscall the Node system call ({@code scandir}, {@code open}, {@code unlink}...)
   * @param path the path
   * @return the unchecked exception to throw
   */
  public static UncheckedIOException nodeError(IOException e, String syscall, Path path) {
    String code;
    String description;
    if (e instanceof NoSuchFileException) {
      code = "ENOENT";
      description = "no such file or directory";
    } else if (e instanceof NotDirectoryException) {
      code = "ENOTDIR";
      description = "not a directory";
    } else if (e instanceof AccessDeniedException) {
      code = "EACCES";
      description = "permission denied";
    } else if (e instanceof DirectoryNotEmptyException) {
      code = "ENOTEMPTY";
      description = "directory not empty";
    } else if (e instanceof FileAlreadyExistsException) {
      code = "EEXIST";
      description = "file already exists";
    } else {
      return new UncheckedIOException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
    }
    return new UncheckedIOException(code + ": " + description + ", " + syscall + " '" + path + "'", e);
  }
}
