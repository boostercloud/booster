package com.boostercloud.framework.provider.local.infrastructure.testhelper;

import com.boostercloud.framework.provider.local.Paths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the documents a local application has stored, from its {@code .booster} files ({@link LocalStoreReader}).
 * Port of {@code test-helper/local-queries.ts}, whose methods are unimplemented stubs returning {@code []}.
 */
public class LocalQueries {

  private static final Pattern EVENT_STREAM_KEY = Pattern.compile("^([^-]+)-(.+)-(event|snapshot)$");

  private final LocalStoreReader reader;

  /** {@code new LocalQueries()}: reads the files of the working directory. */
  public LocalQueries() {
    this(null);
  }

  /**
   * @param baseDirectory the project directory, or {@code null} for the working directory
   */
  public LocalQueries(Path baseDirectory) {
    this.reader = new LocalStoreReader(baseDirectory);
  }

  /**
   * {@code events(primaryKey)}: newest first.
   *
   * @param primaryKey see {@link #events(String, boolean)}
   * @return the stored documents
   */
  public List<Map<String, Object>> events(String primaryKey) {
    return events(primaryKey, true);
  }

  /**
   * {@code events(primaryKey, latestFirst = true)}: the documents of {@code .booster/events.json} (with their
   * {@code _id}) for an event-store key {@code <entityTypeName>-<entityID>-<kind>} (the key the Azure helper uses,
   * {@code Cart-42-event}), or, when the key has another shape, every document of the entity with that id.
   *
   * @param primaryKey the key
   * @param latestFirst whether to sort by {@code createdAt} descending (ascending otherwise)
   * @return the stored documents
   */
  public List<Map<String, Object>> events(String primaryKey, boolean latestFirst) {
    Map<String, Object> query = new LinkedHashMap<>();
    Matcher matcher = EVENT_STREAM_KEY.matcher(primaryKey);
    if (matcher.matches()) {
      query.put("entityTypeName", matcher.group(1));
      query.put("entityID", matcher.group(2));
      query.put("kind", matcher.group(3));
    } else {
      query.put("entityID", primaryKey);
    }
    return reader.find(Paths.EVENTS_DATABASE, query, Map.of("createdAt", latestFirst ? -1 : 1));
  }

  /**
   * {@code readModels(primaryKey, readModelName)}: newest version first.
   *
   * @param primaryKey the read model id
   * @param readModelName the read model name
   * @return the read models
   */
  public List<Map<String, Object>> readModels(String primaryKey, String readModelName) {
    return readModels(primaryKey, readModelName, true);
  }

  /**
   * {@code readModels(primaryKey, readModelName, latestFirst = true)}: the stored read models with that id (the
   * {@code value} of each {@code .booster/read_models.json} document, {@code boosterMetadata} included; normally one).
   *
   * @param primaryKey the read model id
   * @param readModelName the read model name
   * @param latestFirst whether to sort by {@code boosterMetadata.version} descending (ascending otherwise)
   * @return the read models
   */
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> readModels(String primaryKey, String readModelName, boolean latestFirst) {
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("typeName", readModelName);
    query.put("value.id", primaryKey);
    List<Map<String, Object>> documents = reader.find(Paths.READ_MODELS_DATABASE, query,
        Map.of("value.boosterMetadata.version", latestFirst ? -1 : 1));
    List<Map<String, Object>> readModels = new ArrayList<>(documents.size());
    for (Map<String, Object> document : documents) {
      if (document.get("value") instanceof Map<?, ?> value) {
        readModels.add((Map<String, Object>) value);
      }
    }
    return readModels;
  }
}
