package com.boostercloud.framework.provider.local.services;

import com.boostercloud.framework.provider.local.Paths;
import com.boostercloud.framework.provider.local.store.Cursor;
import com.boostercloud.framework.provider.local.store.Datastore;
import com.boostercloud.framework.provider.local.store.DatastoreOptions;
import com.boostercloud.framework.provider.local.store.IndexOptions;
import com.boostercloud.framework.provider.local.store.Js;
import com.boostercloud.framework.provider.local.store.Model;
import com.boostercloud.framework.provider.local.store.NedbError;
import com.boostercloud.framework.provider.local.store.Undefined;
import com.boostercloud.framework.provider.local.store.UpdateOptions;
import com.boostercloud.framework.provider.local.store.UpdateResult;
import com.boostercloud.framework.types.concepts.UUID;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The read model store of the local provider: every read model of every type in one collection
 * ({@code .booster/read_models.json}), as envelopes {@code { typeName, value, uniqueKey }}. Port of
 * {@code services/read-model-registry.ts}.
 *
 * <p>Optimistic concurrency: a read model whose {@code boosterMetadata.version} is {@code 1} is inserted (a unique
 * sparse index on {@code uniqueKey = <typeName>_<id>_<version>} rejects a second version 1); any other version
 * replaces the stored document only if its current {@code value.boosterMetadata.version} equals
 * {@code expectedCurrentVersion} (an atomic compare-and-swap), and otherwise fails with a {@link NedbError} whose
 * {@code errorType} is {@link #UNIQUE_VIOLATED_ERROR_TYPE}.
 */
public class ReadModelRegistry {

  /** {@code UNIQUE_VIOLATED_ERROR_TYPE}: the {@code errorType} of unique and optimistic-concurrency violations. */
  public static final String UNIQUE_VIOLATED_ERROR_TYPE = NedbError.UNIQUE_VIOLATED;

  private final Datastore readModels;
  private volatile boolean indexesEnsured;

  /** A registry on {@code .booster/read_models.json} in the working directory ({@code new ReadModelRegistry()}). */
  public ReadModelRegistry() {
    this(Paths.resolve(null, Paths.READ_MODELS_DATABASE));
  }

  /**
   * @param file the data file
   */
  public ReadModelRegistry(Path file) {
    this(new Datastore(DatastoreOptions.of(file)));
  }

  /**
   * @param datastore the collection to use
   */
  public ReadModelRegistry(Datastore datastore) {
    this.readModels = Objects.requireNonNull(datastore, "datastore");
    this.readModels.ensureLookupIndex("value.id");
    this.readModels.ensureLookupIndex("typeName");
  }

  /**
   * @return the underlying collection ({@code readModelRegistry.readModels})
   */
  public Datastore readModels() {
    return readModels;
  }

  /**
   * @return whether the data file has been loaded
   */
  public boolean isLoaded() {
    return readModels.isLoaded() && indexesEnsured;
  }

  /** {@code loadDatabaseIfNeeded()}: loads the file and ensures the unique sparse index on {@code uniqueKey}. */
  public void loadDatabaseIfNeeded() {
    if (!indexesEnsured || !readModels.isLoaded()) {
      synchronized (this) {
        if (!indexesEnsured || !readModels.isLoaded()) {
          readModels.loadDatabaseIfNeeded();
          readModels.ensureIndex(IndexOptions.unique("uniqueKey", true));
          indexesEnsured = true;
        }
      }
    }
  }

  /**
   * {@code query(query)}.
   *
   * @param query the query
   * @return the matching envelopes (with {@code _id} and {@code uniqueKey})
   */
  public List<Map<String, Object>> query(Map<String, Object> query) {
    return query(query, null, 0, null, null);
  }

  /**
   * {@code query(query, sortBy?, skip?, limit?, select?)}.
   *
   * @param query the query
   * @param sortBy the Booster sort ({@code SortFor} shape), or {@code null}
   * @param skip documents to skip (falsy: none)
   * @param limit maximum number of documents (falsy: no limit)
   * @param select projection paths applied to {@code value} ({@link #filterFields}), or {@code null}
   * @return the matching envelopes
   */
  public List<Map<String, Object>> query(Map<String, Object> query, Map<String, Object> sortBy, double skip,
      Integer limit, List<String> select) {
    loadDatabaseIfNeeded();
    Cursor cursor = readModels.find(query);
    Map<String, Object> sortByList = toLocalSortFor(sortBy);
    if (sortByList != null) {
      cursor = cursor.sort(sortByList);
    }
    if (Js.truthy(skip)) {
      cursor = cursor.skip(skip);
    }
    if (Js.truthy(limit)) {
      cursor = cursor.limit(limit);
    }
    List<Map<String, Object>> results = cursor.exec();
    if (select != null && !select.isEmpty()) {
      for (Map<String, Object> result : results) {
        result.put("value", filterFields(result.get("value"), select));
      }
    }
    return results;
  }

  /**
   * {@code store(readModel, expectedCurrentVersion)}.
   *
   * @param readModel the envelope {@code { typeName, value }} (not modified; the stored copy gets {@code uniqueKey})
   * @param expectedCurrentVersion the version the caller read ({@code null} = JavaScript {@code undefined}, which
   *     never matches a stored version)
   * @throws NedbError with {@code errorType == "uniqueViolated"} on unique or optimistic-concurrency violations
   */
  public void store(Map<String, Object> readModel, Long expectedCurrentVersion) {
    loadDatabaseIfNeeded();
    Map<String, Object> uniqueReadModel = new LinkedHashMap<>(readModel);
    Object value = readModel.get("value");
    Object version = Model.getDotValue(value, "boosterMetadata.version");
    uniqueReadModel.put("uniqueKey", Js.toJsString(readModel.get("typeName")) + "_"
        + Js.toJsString(Js.get(value, "id")) + "_" + Js.toJsString(version));
    if (version instanceof Number number && number.doubleValue() == 1) {
      insert(uniqueReadModel);
      return;
    }
    update(uniqueReadModel, expectedCurrentVersion);
  }

  private void insert(Map<String, Object> readModel) {
    loadDatabaseIfNeeded();
    readModels.insert(readModel);
  }

  private void update(Map<String, Object> readModel, Long expectedCurrentVersion) {
    loadDatabaseIfNeeded();
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("typeName", readModel.get("typeName"));
    query.put("value.id", Js.get(readModel.get("value"), "id"));
    query.put("value.boosterMetadata.version", expectedCurrentVersion == null ? Undefined.INSTANCE : expectedCurrentVersion);
    UpdateResult result = readModels.update(query, readModel, new UpdateOptions(false, false, true));
    if (result != null && result.numAffected() == 0) {
      throw new NedbError("Can't update readModel " + Js.stringify(readModel) + " with expectedCurrentVersion = "
          + (expectedCurrentVersion == null ? "undefined" : expectedCurrentVersion.toString())
          + " . Optimistic concurrency error", UNIQUE_VIOLATED_ERROR_TYPE, null);
    }
  }

  /**
   * {@code deleteById(id, typeName)}: removes at most one read model.
   *
   * @param id the read model id (a {@link UUID} or a raw value)
   * @param typeName the read model name
   * @return the number of removed documents (0 or 1)
   */
  public int deleteById(Object id, String typeName) {
    loadDatabaseIfNeeded();
    Map<String, Object> query = new LinkedHashMap<>();
    query.put("typeName", typeName);
    query.put("value.id", id instanceof UUID uuid ? uuid.value() : id);
    return readModels.remove(query, false);
  }

  /**
   * {@code toLocalSortFor(sortBy)}: turns a Booster sort into a NeDB sort on {@code value.*}. Nested objects give dot
   * paths; <b>only the exact string {@code "ASC"} sorts ascending</b>, every other direction descends (as in
   * TypeScript). Enum directions count as their names.
   *
   * @param sortBy the Booster sort
   * @return the NeDB sort, or {@code null} when {@code sortBy} is absent or empty
   */
  public Map<String, Object> toLocalSortFor(Map<String, Object> sortBy) {
    return toLocalSortFor(sortBy, "", new LinkedHashMap<>());
  }

  private Map<String, Object> toLocalSortFor(Object sortBy, String parentKey, Map<String, Object> sortedList) {
    if (!Js.truthy(sortBy) || Js.keys(sortBy).isEmpty()) {
      return null;
    }
    for (String key : Js.keys(sortBy)) {
      Object value = Js.get(sortBy, key);
      if (value instanceof Enum<?> direction) {
        value = direction.name();
      }
      if (value instanceof String direction) {
        sortedList.put("value." + parentKey + key, direction.equals("ASC") ? 1 : -1);
      } else {
        toLocalSortFor(value, parentKey + key + ".", sortedList);
      }
    }
    return sortedList;
  }

  /**
   * {@code filterFields(obj, select)}: a new object with only the selected paths of {@code obj}. Paths are dot
   * separated; a segment ending in {@code []} applies the rest of the path to every element of that array. TypeScript
   * quirks are kept: after an array segment the remaining segments are also applied to the enclosing object, and a
   * path through an array without {@code []} yields an empty array.
   *
   * @param obj the read model
   * @param select the paths
   * @return the projection
   */
  public Map<String, Object> filterFields(Object obj, List<String> select) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String field : select) {
      setNestedValue(result, obj, List.of(field.split("\\.", -1)));
    }
    return result;
  }

  /**
   * {@code setNestedValue(result, source, parts)}.
   *
   * @param result the object being built
   * @param source the source object
   * @param parts the path segments
   */
  @SuppressWarnings("unchecked")
  public void setNestedValue(Object result, Object source, List<String> parts) {
    Object currentResult = result;
    Object currentSource = source;
    for (int i = 0; i < parts.size(); i++) {
      String part = parts.get(i);
      boolean isLast = i == parts.size() - 1;
      if (part.endsWith("[]")) {
        String arrayField = part.substring(0, part.length() - 2);
        Object sourceArray = property(currentSource, arrayField);
        if (!(sourceArray instanceof List<?> sourceList)) {
          return;
        }
        if (!Js.truthy(property(currentResult, arrayField))) {
          assign(currentResult, arrayField, new ArrayList<>());
        }
        if (isLast) {
          assign(currentResult, arrayField, sourceList);
        } else {
          Object resultArray = property(currentResult, arrayField);
          for (int index = 0; index < sourceList.size(); index++) {
            String indexKey = Integer.toString(index);
            if (!Js.truthy(property(resultArray, indexKey))) {
              assign(resultArray, indexKey, new LinkedHashMap<String, Object>());
            }
            setNestedValue(property(resultArray, indexKey), sourceList.get(index), parts.subList(i + 1, parts.size()));
          }
        }
      } else {
        if (isLast) {
          Object value = property(currentSource, part);
          if (value != Undefined.INSTANCE) {
            assign(currentResult, part, value);
          }
        } else {
          Object next = property(currentSource, part);
          if (!Js.truthy(next)) {
            return;
          }
          if (!Js.truthy(property(currentResult, part))) {
            assign(currentResult, part, next instanceof List<?> ? new ArrayList<>() : new LinkedHashMap<String, Object>());
          }
          currentResult = property(currentResult, part);
          currentSource = next;
        }
      }
    }
  }

  /** {@code obj[key]}, throwing like JavaScript on {@code null}/{@code undefined}. */
  private static Object property(Object obj, String key) {
    if (obj == null || obj == Undefined.INSTANCE) {
      throw new IllegalArgumentException("Cannot read properties of " + Js.toJsString(obj) + " (reading '" + key + "')");
    }
    return Js.get(obj, key);
  }

  @SuppressWarnings("unchecked")
  private static void assign(Object obj, String key, Object value) {
    if (obj instanceof Map<?, ?> map) {
      ((Map<String, Object>) map).put(key, value);
    } else if (obj instanceof List<?> list) {
      int index = Integer.parseInt(key);
      List<Object> elements = (List<Object>) list;
      while (elements.size() <= index) {
        elements.add(null);
      }
      elements.set(index, value);
    }
  }
}
