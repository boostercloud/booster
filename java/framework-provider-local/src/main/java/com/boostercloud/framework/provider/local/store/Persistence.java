package com.boostercloud.framework.provider.local.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Port of NeDB's {@code lib/persistence.js} and {@code lib/storage.js}: reading a data file, appending lines and the
 * crash-safe rewrite used for compaction.
 *
 * <p>Durability: {@link #append(List)} writes the lines and calls {@link FileChannel#force(boolean)} before returning
 * (NeDB does not flush appends). If the write fails the file is truncated back to its previous length, so a failed
 * write never leaves a partial line that a later append would glue onto; if even that fails, the store refuses further
 * writes until it is reloaded. {@link #crashSafeWrite(List)} writes {@code <file>~}, flushes it, renames it over the
 * data file atomically and flushes the directory, exactly like NeDB's {@code crashSafeWriteFileLines};
 * {@link #ensureDatafileIntegrity()} recovers from a crash between those steps.
 *
 * <p>Reading: lines are separated by {@code \n} (a preceding {@code \r} is ignored). A final line that has no
 * terminating newline and cannot be parsed is a write that was interrupted by a crash: it is discarded and does not
 * count as corruption (it was never acknowledged, since acknowledged writes end with a flushed newline). Every other
 * unparsable line is corrupt; when corrupt lines exceed {@code corruptAlertThreshold} of the non-empty lines, loading
 * fails with {@link CorruptDataFileException}.
 */
final class Persistence {

  private final Path file;
  private final Path tempFile;
  private final double corruptAlertThreshold;
  private final Consumer<String> warnings;
  private FileChannel appendChannel;
  private boolean failed;

  Persistence(Path file, double corruptAlertThreshold, Consumer<String> warnings) {
    this.file = file;
    this.tempFile = file.resolveSibling(file.getFileName().toString() + "~");
    this.corruptAlertThreshold = corruptAlertThreshold;
    this.warnings = warnings;
  }

  Path file() {
    return file;
  }

  /** The documents and index definitions found in a data file. */
  record TreatedData(List<Map<String, Object>> data, Map<String, Map<String, Object>> indexes, int dataLength,
      int corruptItems, boolean tornTail) {}

  void ensureParentDirectoryExists() throws IOException {
    Path parent = file.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }

  /** {@code ensureDatafileIntegrity}: a missing data file is restored from {@code <file>~} or created empty. */
  void ensureDatafileIntegrity() throws IOException {
    if (Files.exists(file)) {
      return;
    }
    if (!Files.exists(tempFile)) {
      Files.write(file, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    } else {
      move(tempFile, file);
    }
  }

  /** Reads and replays the data file ({@code treatRawStream}). */
  TreatedData read() throws IOException {
    byte[] bytes = Files.readAllBytes(file);
    String content = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
        .decode(ByteBuffer.wrap(bytes))
        .toString();
    return treat(content);
  }

  @SuppressWarnings("unchecked")
  TreatedData treat(String content) {
    Map<String, Map<String, Object>> dataById = new LinkedHashMap<>();
    Map<String, Map<String, Object>> indexes = new LinkedHashMap<>();
    int corruptItems = 0;
    int length = 0;
    boolean tornTail = false;
    String[] segments = content.split("\n", -1);
    for (int i = 0; i < segments.length; i++) {
      String line = segments[i];
      if (line.endsWith("\r")) {
        line = line.substring(0, line.length() - 1);
      }
      if (line.isEmpty()) {
        continue;
      }
      boolean unterminated = i == segments.length - 1;
      Object doc;
      try {
        doc = Serialization.deserialize(line);
      } catch (IOException | RuntimeException e) {
        if (unterminated) {
          tornTail = true;
          warnings.accept("Discarding an incomplete last line of " + file + " (interrupted write): " + abbreviate(line));
          continue;
        }
        corruptItems++;
        length++;
        continue;
      }
      if (doc == null) {
        // JSON.parse('null')._id throws a TypeError in NeDB, which counts the line as corrupt
        corruptItems++;
      } else if (doc instanceof Map<?, ?> map) {
        Object id = map.containsKey("_id") ? map.get("_id") : Undefined.INSTANCE;
        Object indexCreated = map.get("$$indexCreated");
        Object indexRemoved = map.get("$$indexRemoved");
        if (Js.truthy(id)) {
          if (Boolean.TRUE.equals(map.get("$$deleted"))) {
            dataById.remove(Js.toJsString(id));
          } else {
            dataById.put(Js.toJsString(id), (Map<String, Object>) map);
          }
        } else if (indexCreated instanceof Map<?, ?> definition && definition.get("fieldName") != null) {
          indexes.put(Js.toJsString(definition.get("fieldName")), (Map<String, Object>) definition);
        } else if (indexRemoved instanceof String fieldName) {
          indexes.remove(fieldName);
        }
      }
      length++;
    }
    if (length > 0) {
      double corruptionRate = (double) corruptItems / length;
      if (corruptionRate > corruptAlertThreshold) {
        throw new CorruptDataFileException(file, corruptionRate, corruptItems, length, corruptAlertThreshold);
      }
    }
    if (corruptItems > 0) {
      warnings.accept("Skipped " + corruptItems + " corrupt line(s) of " + file);
    }
    return new TreatedData(new ArrayList<>(dataById.values()), indexes, length, corruptItems, tornTail);
  }

  private static String abbreviate(String line) {
    return line.length() <= 80 ? line : line.substring(0, 77) + "...";
  }

  /**
   * Appends lines and flushes them to the storage device before returning.
   *
   * @param lines serialized documents
   */
  void append(List<String> lines) {
    if (lines.isEmpty()) {
      return;
    }
    if (failed) {
      throw new NedbError("The data file " + file + " could not be repaired after a failed write; reload the "
          + "datastore to continue");
    }
    StringBuilder text = new StringBuilder();
    for (String line : lines) {
      text.append(line).append('\n');
    }
    ByteBuffer buffer = StandardCharsets.UTF_8.encode(text.toString());
    long sizeBefore = -1;
    try {
      FileChannel channel = appendChannel();
      sizeBefore = channel.size();
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(false);
    } catch (IOException e) {
      rollback(sizeBefore);
      throw new NedbError("Could not write to " + file + ": " + e.getMessage(), e);
    }
  }

  private void rollback(long sizeBefore) {
    if (sizeBefore < 0) {
      return;
    }
    try {
      appendChannel.truncate(sizeBefore);
      appendChannel.force(false);
    } catch (IOException | RuntimeException e) {
      failed = true;
    }
  }

  private FileChannel appendChannel() throws IOException {
    if (appendChannel == null || !appendChannel.isOpen()) {
      appendChannel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
          StandardOpenOption.APPEND);
    }
    return appendChannel;
  }

  /**
   * {@code crashSafeWriteFileLines}: replaces the data file with the given lines, atomically.
   *
   * @param lines serialized documents and index definitions
   */
  void crashSafeWrite(List<String> lines) throws IOException {
    closeAppendChannel();
    ensureParentDirectoryExists();
    Path directory = file.toAbsolutePath().getParent();
    if (Files.exists(file)) {
      flushDirectory(directory);
    }
    StringBuilder text = new StringBuilder();
    for (String line : lines) {
      text.append(line).append('\n');
    }
    ByteBuffer buffer = StandardCharsets.UTF_8.encode(text.toString());
    try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING)) {
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
    }
    move(tempFile, file);
    flushDirectory(directory);
    failed = false;
  }

  private static void move(Path from, Path to) throws IOException {
    try {
      Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** {@code flushToStorage({ isDir: true })}: best effort, some platforms cannot sync directories. */
  private static void flushDirectory(Path directory) {
    if (directory == null) {
      return;
    }
    try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
      channel.force(true);
    } catch (IOException | RuntimeException ignored) {
      // Windows and some file systems do not allow opening or syncing directories
    }
  }

  void closeAppendChannel() {
    if (appendChannel != null) {
      try {
        appendChannel.close();
      } catch (IOException ignored) {
        // nothing left to flush: every append was forced
      }
      appendChannel = null;
    }
  }

  /** Clears the failed state after a successful reload. */
  void resetFailure() {
    failed = false;
  }
}
