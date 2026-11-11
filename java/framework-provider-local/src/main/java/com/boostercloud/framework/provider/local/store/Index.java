package com.boostercloud.framework.provider.local.store;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Port of NeDB's {@code lib/indexes.js}: an ordered map from keys ({@link Model#compareThings} order) to the
 * {@code _id}s of the documents holding them. Array fields index each distinct element; unique indexes reject a key
 * that is already present ({@link NedbError#uniqueViolated}); sparse indexes skip documents without the field.
 *
 * <p>Within one key, documents are kept in {@code _id} order. (NeDB keeps them in insertion order; after a load that is
 * the {@code _id} order too, and the store's generated ids grow monotonically, so the two orders coincide for
 * documents written by this store.)
 *
 * <p>A <em>lookup</em> index is the Java store's own, memory-only accelerator: it indexes every leaf of nested arrays
 * so that it never misses a candidate of a basic-equality query, and is never persisted nor used for {@code $in} or
 * range candidates.
 */
final class Index {

  private final String fieldName;
  private final List<String> fields;
  private final boolean unique;
  private final boolean sparse;
  private final boolean lookup;
  private TreeMap<Object, TreeSet<Object>> tree = newTree();

  Index(IndexOptions options) {
    this(options.fieldName(), options.isUnique(), options.isSparse(), false);
  }

  Index(String fieldName, boolean unique, boolean sparse, boolean lookup) {
    this.fieldName = fieldName;
    this.fields = List.of(fieldName.split(","));
    this.unique = unique;
    this.sparse = sparse;
    this.lookup = lookup;
  }

  private static TreeMap<Object, TreeSet<Object>> newTree() {
    return new TreeMap<>(Model::compareThings);
  }

  String fieldName() {
    return fieldName;
  }

  boolean unique() {
    return unique;
  }

  boolean sparse() {
    return sparse;
  }

  private Object keyOf(Map<String, Object> document) {
    if (fields.size() == 1) {
      return Model.getDotValue(document, fieldName);
    }
    Map<String, Object> key = new LinkedHashMap<>();
    for (String field : fields) {
      key.put(field, Model.getDotValue(document, field));
    }
    return key;
  }

  private boolean skipped(Object key) {
    if (!sparse) {
      return false;
    }
    if (key == Undefined.INSTANCE) {
      return true;
    }
    if (fields.size() > 1 && key instanceof Map<?, ?> compound) {
      return compound.values().stream().allMatch(value -> value == Undefined.INSTANCE);
    }
    return false;
  }

  /** {@code uniq(keys, projectForUnique)}: distinct primitive elements by type and value, objects by identity. */
  private static List<Object> distinctKeys(List<?> keys) {
    Map<Object, Object> byProjection = new LinkedHashMap<>();
    Map<Object, Object> objects = new IdentityHashMap<>();
    List<Object> result = new ArrayList<>();
    for (Object key : keys) {
      Object projection = projectForUnique(key);
      if (projection == null) {
        if (objects.put(key, key) == null) {
          result.add(key);
        }
      } else if (!byProjection.containsKey(projection)) {
        byProjection.put(projection, key);
        result.add(key);
      }
    }
    return result;
  }

  private static Object projectForUnique(Object element) {
    if (element == null) {
      return "$null";
    }
    if (element instanceof String s) {
      return "$string" + s;
    }
    if (element instanceof Boolean b) {
      return "$boolean" + b;
    }
    if (element instanceof Number n) {
      return "$number" + Js.toJsString(n);
    }
    if (element instanceof NedbDate date) {
      return "$date" + Js.toJsString(date.epochMillis());
    }
    return null;
  }

  private static void flattenLeaves(Object value, List<Object> leaves) {
    if (value instanceof List<?> list) {
      for (Object element : list) {
        flattenLeaves(element, leaves);
      }
    } else if (value != Undefined.INSTANCE) {
      leaves.add(value);
    }
  }

  private List<Object> keysOf(Map<String, Object> document) {
    Object key = keyOf(document);
    if (lookup) {
      List<Object> leaves = new ArrayList<>();
      flattenLeaves(key, leaves);
      return distinctKeys(leaves);
    }
    if (skipped(key)) {
      return List.of();
    }
    if (key instanceof List<?> list) {
      return distinctKeys(list);
    }
    List<Object> single = new ArrayList<>(1);
    single.add(key);
    return single;
  }

  /**
   * Indexes a document; on a unique violation nothing is indexed and the error is thrown.
   *
   * @param document the document (must have an {@code _id})
   */
  void insert(Map<String, Object> document) {
    Object id = document.get("_id");
    List<Object> keys = keysOf(document);
    List<Object> inserted = new ArrayList<>();
    for (Object key : keys) {
      TreeSet<Object> ids = tree.get(key);
      if (ids != null && !ids.isEmpty() && unique) {
        for (Object done : inserted) {
          removeKey(done, id);
        }
        throw NedbError.uniqueViolated(key);
      }
      tree.computeIfAbsent(key, k -> new TreeSet<>(Model::compareThings)).add(id);
      inserted.add(key);
    }
  }

  /**
   * Indexes several documents, all or nothing.
   *
   * @param documents the documents
   */
  void insert(List<Map<String, Object>> documents) {
    int failingIndex = -1;
    NedbError error = null;
    for (int i = 0; i < documents.size(); i++) {
      try {
        insert(documents.get(i));
      } catch (NedbError e) {
        error = e;
        failingIndex = i;
        break;
      }
    }
    if (error != null) {
      for (int i = 0; i < failingIndex; i++) {
        remove(documents.get(i));
      }
      throw error;
    }
  }

  private void removeKey(Object key, Object id) {
    TreeSet<Object> ids = tree.get(key);
    if (ids != null) {
      ids.remove(id);
      if (ids.isEmpty()) {
        tree.remove(key);
      }
    }
  }

  /**
   * @param document a previously indexed document
   */
  void remove(Map<String, Object> document) {
    Object id = document.get("_id");
    for (Object key : keysOf(document)) {
      removeKey(key, id);
    }
  }

  /** A document replaced by a new version (same {@code _id}). */
  record Modification(Map<String, Object> oldDoc, Map<String, Object> newDoc) {}

  /**
   * {@code updateMultipleDocs(pairs)}: re-indexes modified documents, all or nothing.
   *
   * @param modifications the old and new versions
   */
  void update(List<Modification> modifications) {
    for (Modification modification : modifications) {
      remove(modification.oldDoc());
    }
    int failingIndex = -1;
    NedbError error = null;
    for (int i = 0; i < modifications.size(); i++) {
      try {
        insert(modifications.get(i).newDoc());
      } catch (NedbError e) {
        error = e;
        failingIndex = i;
        break;
      }
    }
    if (error != null) {
      for (int i = 0; i < failingIndex; i++) {
        remove(modifications.get(i).newDoc());
      }
      for (Modification modification : modifications) {
        insert(modification.oldDoc());
      }
      throw error;
    }
  }

  /**
   * @param modifications modifications applied by {@link #update(List)}
   */
  void revertUpdate(List<Modification> modifications) {
    List<Modification> revert = new ArrayList<>();
    for (Modification modification : modifications) {
      revert.add(new Modification(modification.newDoc(), modification.oldDoc()));
    }
    update(revert);
  }

  /** Empties the index. */
  void reset() {
    tree = newTree();
  }

  /**
   * Empties the index and indexes the given documents.
   *
   * @param documents the documents
   */
  void reset(List<Map<String, Object>> documents) {
    reset();
    insert(documents);
  }

  /**
   * {@code getMatching(value)}: the ids of the documents with that key, or with any of the keys of an array value
   * (in order of first appearance).
   *
   * @param value a key or an array of keys
   * @return the ids
   */
  List<Object> getMatching(Object value) {
    if (!(value instanceof List<?> values)) {
      TreeSet<Object> ids = tree.get(value);
      return ids == null ? List.of() : new ArrayList<>(ids);
    }
    Set<Object> result = new LinkedHashSet<>();
    for (Object element : values) {
      result.addAll(getMatching(element));
    }
    return new ArrayList<>(result);
  }

  /**
   * {@code getBetweenBounds(query)}: the ids of the documents whose key satisfies {@code $gt}/{@code $gte}/
   * {@code $lt}/{@code $lte}, in key order.
   *
   * @param query the bounds
   * @return the ids
   */
  List<Object> getBetweenBounds(Map<?, ?> query) {
    Predicate<Object> lower = lowerBound(query);
    Predicate<Object> upper = upperBound(query);
    List<Object> result = new ArrayList<>();
    for (Map.Entry<Object, TreeSet<Object>> entry : tree.entrySet()) {
      if (lower.test(entry.getKey()) && upper.test(entry.getKey())) {
        result.addAll(entry.getValue());
      }
    }
    return result;
  }

  private static Predicate<Object> lowerBound(Map<?, ?> query) {
    boolean gt = query.containsKey("$gt");
    boolean gte = query.containsKey("$gte");
    Object gtValue = query.get("$gt");
    Object gteValue = query.get("$gte");
    if (!gt && !gte) {
      return key -> true;
    }
    if (gt && gte) {
      int comparison = Model.compareThings(gteValue, gtValue);
      if (comparison == 0) {
        return key -> Model.compareThings(key, gtValue) > 0;
      }
      if (comparison > 0) {
        return key -> Model.compareThings(key, gteValue) >= 0;
      }
      return key -> Model.compareThings(key, gtValue) > 0;
    }
    if (gt) {
      return key -> Model.compareThings(key, gtValue) > 0;
    }
    return key -> Model.compareThings(key, gteValue) >= 0;
  }

  private static Predicate<Object> upperBound(Map<?, ?> query) {
    boolean lt = query.containsKey("$lt");
    boolean lte = query.containsKey("$lte");
    Object ltValue = query.get("$lt");
    Object lteValue = query.get("$lte");
    if (!lt && !lte) {
      return key -> true;
    }
    if (lt && lte) {
      int comparison = Model.compareThings(lteValue, ltValue);
      if (comparison == 0) {
        return key -> Model.compareThings(key, ltValue) < 0;
      }
      if (comparison < 0) {
        return key -> Model.compareThings(key, lteValue) <= 0;
      }
      return key -> Model.compareThings(key, ltValue) < 0;
    }
    if (lt) {
      return key -> Model.compareThings(key, ltValue) < 0;
    }
    return key -> Model.compareThings(key, lteValue) <= 0;
  }

  /**
   * @return the number of distinct keys
   */
  int numberOfKeys() {
    return tree.size();
  }
}
