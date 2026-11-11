package com.boostercloud.integration.providerspecific.local.durability;

import com.boostercloud.framework.provider.local.Paths;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalStoreReader;
import com.boostercloud.integration.harness.AppProcess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw access to the local provider's data files ({@code <project>/.booster/*.json}, NeDB format) of an application
 * launched by the harness: documents as the store replays them ({@link LocalStoreReader}, read-only, safe while the
 * application runs) and the bytes and lines as they are on disk, plus writers that damage a file the way a crash or
 * a broken disk would.
 */
final class StoreFiles {

  /** {@code .booster/events.json}. */
  static final String EVENTS = Paths.EVENTS_DATABASE;
  /** {@code .booster/read_models.json}. */
  static final String READ_MODELS = Paths.READ_MODELS_DATABASE;
  /** {@code .booster/subscriptions.json}. */
  static final String SUBSCRIPTIONS = Paths.SUBSCRIPTION_DATABASE;
  /** {@code .booster/dispatch_journal.json}. */
  static final String DISPATCH_JOURNAL = Paths.DISPATCH_JOURNAL_DATABASE;

  private final AppProcess app;
  private final LocalStoreReader reader;

  private StoreFiles(AppProcess app) {
    this.app = app;
    this.reader = new LocalStoreReader(app.projectDirectory());
  }

  /**
   * @param app an application launched by the harness
   * @return its data files
   */
  static StoreFiles of(AppProcess app) {
    return new StoreFiles(app);
  }

  /**
   * @param relativePath a data file ({@link #EVENTS}...)
   * @return its path
   */
  Path file(String relativePath) {
    return app.projectDirectory().resolve(relativePath);
  }

  /**
   * @param relativePath a data file
   * @return its current documents (the last version of each {@code _id}), in {@code _id} order
   */
  List<Map<String, Object>> documents(String relativePath) {
    return reader.documents(relativePath);
  }

  /**
   * @param relativePath a data file
   * @param query a NeDB query
   * @return the matching documents, in {@code _id} order
   */
  List<Map<String, Object>> find(String relativePath, Map<String, Object> query) {
    return reader.find(relativePath, query, null);
  }

  /**
   * @param typeName an event type
   * @return the stored events of that type ({@code kind: "event"})
   */
  List<Map<String, Object>> events(String typeName) {
    return find(EVENTS, query("kind", "event", "typeName", typeName));
  }

  /**
   * @param typeName an event type
   * @param entityID the entity
   * @return the stored events of that type for that entity
   */
  List<Map<String, Object>> events(String typeName, String entityID) {
    return find(EVENTS, query("kind", "event", "typeName", typeName, "entityID", entityID));
  }

  /**
   * @param entityTypeName an entity name
   * @param entityID the entity
   * @return its stored snapshots
   */
  List<Map<String, Object>> snapshots(String entityTypeName, String entityID) {
    return find(EVENTS, query("kind", "snapshot", "entityTypeName", entityTypeName, "entityID", entityID));
  }

  /**
   * @param typeName a read model name
   * @return the stored read models of that type (documents {@code {typeName, value, uniqueKey, _id}})
   */
  List<Map<String, Object>> readModels(String typeName) {
    return find(READ_MODELS, query("typeName", typeName));
  }

  /**
   * @param typeName a read model name
   * @param id its id
   * @return its stored value ({@code boosterMetadata} included), or {@code null}
   */
  @SuppressWarnings("unchecked")
  Map<String, Object> readModel(String typeName, String id) {
    List<Map<String, Object>> documents = find(READ_MODELS, query("typeName", typeName, "value.id", id));
    return documents.isEmpty() ? null : (Map<String, Object>) documents.getLast().get("value");
  }

  /**
   * @param relativePath a data file
   * @return its bytes (empty when it does not exist)
   */
  byte[] bytes(String relativePath) {
    try {
      Path file = file(relativePath);
      return Files.exists(file) ? Files.readAllBytes(file) : new byte[0];
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @param relativePath a data file
   * @return its lines as they are on disk (a last line without newline included)
   */
  List<String> lines(String relativePath) {
    return new String(bytes(relativePath), StandardCharsets.UTF_8).lines().toList();
  }

  /**
   * Appends text as it is (no newline added): what a write cut short by a crash leaves behind.
   *
   * @param relativePath a data file
   * @param text the bytes to append
   */
  void append(String relativePath, String text) {
    try {
      Files.writeString(file(relativePath), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Replaces the file's bytes.
   *
   * @param relativePath a data file
   * @param contents the new contents
   */
  void write(String relativePath, byte[] contents) {
    try {
      Files.write(file(relativePath), contents);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @param keysAndValues alternating field names and values
   * @return a NeDB equality query
   */
  static Map<String, Object> query(Object... keysAndValues) {
    Map<String, Object> query = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      query.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return query;
  }
}
