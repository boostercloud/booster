package com.boostercloud.framework.provider.local.store;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Options of {@link Datastore#ensureIndex(IndexOptions)}, NeDB's {@code ensureIndexAsync({ fieldName, unique, sparse,
 * expireAfterSeconds })}. Absent ({@code null}) options are not written to the {@code $$indexCreated} line, exactly
 * like NeDB persists only the options it was given.
 *
 * @param fieldName the indexed field (a dot path; several fields separated by commas make a compound index)
 * @param unique whether two documents may not share a key, or {@code null}
 * @param sparse whether documents without the field are left out of the index, or {@code null}
 * @param expireAfterSeconds TTL in seconds: documents whose field holds a date older than this are removed, or
 *     {@code null}
 */
public record IndexOptions(String fieldName, Boolean unique, Boolean sparse, Integer expireAfterSeconds) {

  /**
   * @param fieldName the field
   * @return a non-unique, non-sparse index
   */
  public static IndexOptions of(String fieldName) {
    return new IndexOptions(fieldName, null, null, null);
  }

  /**
   * @param fieldName the field
   * @param sparse whether documents without the field are left out
   * @return a unique index
   */
  public static IndexOptions unique(String fieldName, boolean sparse) {
    return new IndexOptions(fieldName, true, sparse, null);
  }

  /**
   * @param fieldName the field
   * @param expireAfterSeconds the TTL
   * @return a TTL index
   */
  public static IndexOptions ttl(String fieldName, int expireAfterSeconds) {
    return new IndexOptions(fieldName, null, null, expireAfterSeconds);
  }

  /**
   * @return whether the index is unique ({@code options.unique || false})
   */
  public boolean isUnique() {
    return Boolean.TRUE.equals(unique);
  }

  /**
   * @return whether the index is sparse ({@code options.sparse || false})
   */
  public boolean isSparse() {
    return Boolean.TRUE.equals(sparse);
  }

  /**
   * @return the options as NeDB writes them in a {@code $$indexCreated} line
   */
  Map<String, Object> toDocument() {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("fieldName", fieldName);
    if (unique != null) {
      document.put("unique", unique);
    }
    if (sparse != null) {
      document.put("sparse", sparse);
    }
    if (expireAfterSeconds != null) {
      document.put("expireAfterSeconds", expireAfterSeconds);
    }
    return document;
  }

  /**
   * @param document a {@code $$indexCreated} value read from a data file
   * @return the options
   */
  static IndexOptions fromDocument(Map<?, ?> document) {
    Object expire = document.get("expireAfterSeconds");
    return new IndexOptions(
        Js.toJsString(document.get("fieldName")),
        document.containsKey("unique") ? Js.truthy(document.get("unique")) : null,
        document.containsKey("sparse") ? Js.truthy(document.get("sparse")) : null,
        expire instanceof Number number ? number.intValue() : null);
  }
}
