package com.boostercloud.framework.provider.local.infrastructure.testhelper;

import com.boostercloud.framework.provider.local.Paths;
import com.boostercloud.framework.provider.local.store.Datastore;
import com.boostercloud.framework.provider.local.store.DatastoreOptions;
import com.boostercloud.framework.provider.local.store.Serialization;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the data files of a local application ({@code .booster/*.json}) without opening them as a store: the file
 * is read as it is on disk, its lines are replayed the way NeDB loads them (the last version of each {@code _id}
 * wins, {@code $$deleted} lines remove documents, index lines are skipped) and the result is queried in memory with
 * the store's matching and sorting rules.
 *
 * <p>Nothing is written, so the files can be read while the application (possibly another process) writes them: an
 * unparsable line (the tail of a write in progress) is skipped, and a file being compacted is read from
 * {@code <file>~} when the data file is briefly missing.
 */
public final class LocalStoreReader {

  private final Path baseDirectory;

  /**
   * @param baseDirectory the project directory holding {@code .booster}, or {@code null} for the working directory
   */
  public LocalStoreReader(Path baseDirectory) {
    this.baseDirectory = baseDirectory;
  }

  /**
   * @return the project directory, or {@code null} for the working directory
   */
  public Path baseDirectory() {
    return baseDirectory;
  }

  /**
   * @param relativePath a data file ({@code Paths.EVENTS_DATABASE}...)
   * @return its path
   */
  public Path file(String relativePath) {
    return Paths.resolve(baseDirectory, relativePath);
  }

  /**
   * @param relativePath a data file
   * @return its current documents, in {@code _id} order (empty when the file does not exist)
   */
  public List<Map<String, Object>> documents(String relativePath) {
    try (Datastore snapshot = snapshot(relativePath)) {
      return snapshot.getAllData();
    }
  }

  /**
   * @param relativePath a data file
   * @param query a NeDB query
   * @return the number of matching documents
   */
  public long count(String relativePath, Map<String, Object> query) {
    try (Datastore snapshot = snapshot(relativePath)) {
      return snapshot.count(query);
    }
  }

  /**
   * @param relativePath a data file
   * @param query a NeDB query
   * @param sort a NeDB sort ({@code field: 1 | -1}), or {@code null}
   * @return the matching documents
   */
  public List<Map<String, Object>> find(String relativePath, Map<String, Object> query, Map<String, Object> sort) {
    try (Datastore snapshot = snapshot(relativePath)) {
      return snapshot.find(query).sort(sort).exec();
    }
  }

  /**
   * @param relativePath a data file
   * @return an in-memory datastore holding the current documents of the file (close it when done)
   */
  public Datastore snapshot(String relativePath) {
    Datastore datastore = new Datastore(DatastoreOptions.inMemory());
    List<Map<String, Object>> documents = replay(read(file(relativePath)));
    if (!documents.isEmpty()) {
      datastore.insert(documents);
    }
    return datastore;
  }

  private static String read(Path file) {
    for (Path candidate : List.of(file, file.resolveSibling(file.getFileName() + "~"))) {
      try {
        byte[] bytes = Files.readAllBytes(candidate);
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .decode(ByteBuffer.wrap(bytes))
            .toString();
      } catch (NoSuchFileException e) {
        // try the next candidate
      } catch (IOException e) {
        throw new UncheckedIOException("Could not read " + candidate + ": " + e.getMessage(), e);
      }
    }
    return "";
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> replay(String content) {
    Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
    for (String rawLine : content.split("\n", -1)) {
      String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
      if (line.isBlank()) {
        continue;
      }
      Object document;
      try {
        document = Serialization.deserialize(line);
      } catch (IOException | RuntimeException e) {
        continue;
      }
      if (!(document instanceof Map<?, ?> map) || !(map.get("_id") instanceof String id) || id.isEmpty()) {
        continue;
      }
      if (Boolean.TRUE.equals(map.get("$$deleted"))) {
        byId.remove(id);
      } else {
        byId.remove(id);
        byId.put(id, (Map<String, Object>) map);
      }
    }
    return new ArrayList<>(byId.values());
  }
}
