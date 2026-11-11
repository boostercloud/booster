package com.boostercloud.framework.provider.local.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
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
 * <p>Atomic batches: an append of several lines (an insert of several documents, a multi update or remove) starts
 * with a header line {@code {"$$batch":<n>}}. On load the {@code n} lines that follow are applied only if all of them
 * are present and parse, so a crash that cuts such an append short (a power loss before the flush completed, or a
 * short write) never leaves part of it behind. NeDB reads these files too: it ignores the header line, like any line
 * without {@code _id}.
 *
 * <p>Reading: lines are separated by {@code \n} (a preceding {@code \r} is ignored). A final line that has no
 * terminating newline and cannot be parsed is a write that was interrupted by a crash: it is discarded and does not
 * count as corruption (it was never acknowledged, since acknowledged writes end with a flushed newline). The same holds
 * for a batch at the end of the file that is incomplete or has an unusable line. Every other unparsable line (and every
 * line of a damaged batch that more lines follow) is corrupt; when corrupt lines exceed {@code corruptAlertThreshold}
 * of the non-empty lines, loading fails with {@link CorruptDataFileException}.
 */
final class Persistence {

  /** Opens the channels the store writes through; replaceable by tests to inject write failures. */
  @FunctionalInterface
  interface ChannelFactory {
    /** The real file system. */
    ChannelFactory DEFAULT = FileChannel::open;

    /**
     * @param path the file
     * @param options how to open it
     * @return the channel
     * @throws IOException when it cannot be opened
     */
    FileChannel open(Path path, OpenOption... options) throws IOException;
  }

  private final Path file;
  private final Path tempFile;
  private final double corruptAlertThreshold;
  private final Consumer<String> warnings;
  private final ChannelFactory channels;
  private FileChannel appendChannel;
  private boolean failed;

  Persistence(Path file, double corruptAlertThreshold, Consumer<String> warnings) {
    this(file, corruptAlertThreshold, warnings, ChannelFactory.DEFAULT);
  }

  Persistence(Path file, double corruptAlertThreshold, Consumer<String> warnings, ChannelFactory channels) {
    this.file = file;
    this.tempFile = file.resolveSibling(file.getFileName().toString() + "~");
    this.corruptAlertThreshold = corruptAlertThreshold;
    this.warnings = warnings;
    this.channels = channels;
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

  /** The key of the line that opens an atomic batch: {@code {"$$batch":<number of lines that follow>}}. */
  static final String BATCH_KEY = Datastore.BATCH_HEADER_KEY;

  /** A line that is not valid JSON. */
  private static final Object UNPARSABLE = new Object();

  /**
   * @param lines the number of lines of an append
   * @return the lines the append writes besides them: the batch header of a multi-line append
   */
  static int framingLines(int lines) {
    return lines > 1 ? 1 : 0;
  }

  /**
   * @param doc a parsed line
   * @return the number of lines of the batch this line opens, or {@code null} when it is not a batch header
   */
  private static Integer batchSize(Object doc) {
    if (doc instanceof Map<?, ?> map && map.size() == 1 && map.get(BATCH_KEY) instanceof Number size) {
      double value = size.doubleValue();
      if (value >= 1 && value <= Integer.MAX_VALUE && value == Math.rint(value)) {
        return (int) value;
      }
    }
    return null;
  }

  private static Object parse(String line) {
    try {
      return Serialization.deserialize(line);
    } catch (IOException | RuntimeException e) {
      return UNPARSABLE;
    }
  }

  /**
   * Replays the lines of a data file ({@code treatRawData}), honouring the atomic batches {@link #append(List)} writes:
   * the lines of a batch are applied only when every one of them is present and parses. A batch cut short at the end
   * of the file (or with an unusable line and nothing after it) is a write interrupted by a crash, like a torn last
   * line: it is discarded with a warning and counts neither as data nor as corruption. A damaged batch followed by
   * more lines was acknowledged and later damaged: none of its lines is applied and all of them count as corrupt.
   */
  @SuppressWarnings("unchecked")
  TreatedData treat(String content) {
    Map<String, Map<String, Object>> dataById = new LinkedHashMap<>();
    Map<String, Map<String, Object>> indexes = new LinkedHashMap<>();
    int corruptItems = 0;
    int length = 0;
    boolean tornTail = false;
    List<Object> batch = null;
    int expectedBatchLines = 0;
    List<Object> damagedBatch = null;
    String[] segments = content.split("\n", -1);
    for (int i = 0; i < segments.length; i++) {
      String line = segments[i];
      if (line.endsWith("\r")) {
        line = line.substring(0, line.length() - 1);
      }
      if (line.isEmpty()) {
        continue;
      }
      if (damagedBatch != null) {
        // more lines follow, so the damaged batch was a complete write that got damaged afterwards: its lines are lost
        corruptItems += damagedBatch.size();
        damagedBatch = null;
      }
      boolean unterminated = i == segments.length - 1;
      Object doc = parse(line);
      Integer header = batchSize(doc);
      if (batch != null && header == null) {
        batch.add(doc);
        length++;
        if (batch.size() == expectedBatchLines) {
          if (batch.stream().allMatch(parsed -> parsed instanceof Map<?, ?>)) {
            for (Object batchLine : batch) {
              apply((Map<String, Object>) batchLine, dataById, indexes);
            }
          } else {
            damagedBatch = batch;
          }
          batch = null;
        }
        continue;
      }
      if (batch != null) {
        // a batch header before the previous batch was complete: the lines of that batch are lost
        corruptItems += batch.size();
        batch = null;
      }
      if (header != null) {
        batch = new ArrayList<>(header);
        expectedBatchLines = header;
        length++;
        continue;
      }
      if (doc == UNPARSABLE) {
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
        apply((Map<String, Object>) map, dataById, indexes);
      }
      length++;
    }
    List<Object> interrupted = batch != null ? batch : damagedBatch;
    if (interrupted != null) {
      // the last write of the file is an atomic batch that never completed: discarded as a whole, header included
      tornTail = true;
      length -= interrupted.size() + 1;
      warnings.accept("Discarding an incomplete batch of " + (batch != null ? expectedBatchLines : interrupted.size())
          + " line(s) at the end of " + file + " (interrupted write; " + interrupted.size() + " line(s) present"
          + (batch != null ? "" : ", not all of them readable") + ")");
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

  /** Applies one parsed line: a document version, a deletion or an index definition (other lines are ignored). */
  @SuppressWarnings("unchecked")
  private static void apply(Map<String, Object> map, Map<String, Map<String, Object>> dataById,
      Map<String, Map<String, Object>> indexes) {
    Object id = map.containsKey("_id") ? map.get("_id") : Undefined.INSTANCE;
    Object indexCreated = map.get("$$indexCreated");
    Object indexRemoved = map.get("$$indexRemoved");
    if (Js.truthy(id)) {
      if (Boolean.TRUE.equals(map.get("$$deleted"))) {
        dataById.remove(Js.toJsString(id));
      } else {
        dataById.put(Js.toJsString(id), map);
      }
    } else if (indexCreated instanceof Map<?, ?> definition && definition.get("fieldName") != null) {
      indexes.put(Js.toJsString(definition.get("fieldName")), (Map<String, Object>) definition);
    } else if (indexRemoved instanceof String fieldName) {
      indexes.remove(fieldName);
    }
  }

  private static String abbreviate(String line) {
    return line.length() <= 80 ? line : line.substring(0, 77) + "...";
  }

  /**
   * Appends lines and flushes them to the storage device before returning. Several lines are written as one atomic
   * batch: a header line {@code {"$$batch":<n>}} first, so that a crash that cuts the append short leaves a batch the
   * next load discards as a whole (NeDB ignores the header, a line without {@code _id}).
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
    if (framingLines(lines.size()) > 0) {
      text.append("{\"").append(BATCH_KEY).append("\":").append(lines.size()).append("}\n");
    }
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
      appendChannel = channels.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
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
    try (FileChannel channel = channels.open(tempFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
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
