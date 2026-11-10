package com.boostercloud.rocket.fileuploads.local.handlers;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.rocket.fileuploads.local.LocalStorage;
import com.boostercloud.rocket.fileuploads.types.ListItem;
import com.boostercloud.rocket.fileuploads.types.ListItemProperties;
import com.boostercloud.rocket.fileuploads.types.NodePath;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Port of {@code handlers/list.ts} (local; Java name {@code ListHandler} because {@code List} is taken).
 */
public final class ListHandler {

  /** Byte-wise order of the UTF-8 names: libuv sorts {@code scandir} results with {@code strcmp}. */
  private static final Comparator<String> STRCMP = (a, b) ->
      Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));

  private ListHandler() {}

  /**
   * {@code list(_config, rocketFilesUserConfiguration, directory)}: the entries (files, sub-directories and dot files,
   * not recursive) of {@code <project>/<storage>/<container>/<directory>}, sorted by name, each with
   * {@code name = path.join(directory, entry)} and only {@code properties.lastModified}, which is the inode change time
   * ({@code ctime}, as TypeScript's {@code stats.ctime}; the modification time where the file system has no
   * {@code ctime}).
   *
   * @param config the application configuration (for the project directory)
   * @param rocketFilesUserConfiguration the storage
   * @param directory the directory
   * @return the entries
   * @throws java.io.UncheckedIOException {@code ENOENT: no such file or directory, scandir '<path>'} when the
   *     directory does not exist
   */
  public static List<ListItem> list(BoosterConfig config, RocketFilesUserConfiguration rocketFilesUserConfiguration,
      String directory) {
    String joined = NodePath.join(LocalStorage.projectDirectory(config).toString(),
        rocketFilesUserConfiguration.storageName(), rocketFilesUserConfiguration.containerName(), directory);
    Path path = Path.of(joined);
    List<String> files = new ArrayList<>();
    try (Stream<Path> entries = Files.list(path)) {
      entries.forEach(entry -> files.add(entry.getFileName().toString()));
    } catch (IOException e) {
      throw LocalStorage.nodeError(e, "scandir", joined);
    }
    files.sort(STRCMP);
    List<ListItem> result = new ArrayList<>();
    for (String file : files) {
      Path filePath = path.resolve(file);
      Instant changed;
      try {
        changed = changeTime(filePath);
      } catch (IOException e) {
        throw LocalStorage.nodeError(e, "stat", filePath);
      }
      result.add(ListItem.of(NodePath.join(directory, file), ListItemProperties.lastModified(changed)));
    }
    return result;
  }

  private static Instant changeTime(Path file) throws IOException {
    FileTime time;
    try {
      time = (FileTime) Files.getAttribute(file, "unix:ctime");
    } catch (UnsupportedOperationException | IllegalArgumentException noUnixView) {
      time = Files.getLastModifiedTime(file);
    }
    return time.toInstant().truncatedTo(ChronoUnit.MILLIS);
  }
}
