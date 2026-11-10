package com.boostercloud.framework.types.concepts;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Framework-owned metadata of a stored read model ({@code readModel.boosterMetadata}), rewritten on every store.
 * Port of {@code BoosterMetadata}. Unknown keys of a stored document are kept in {@link #additionalProperties()} so a
 * read-modify-write preserves them (TypeScript spreads the previous metadata).
 *
 * @param version optimistic-concurrency counter; {@code 0} means "not stored yet", the first stored value is 1
 * @param schemaVersion schema version of the stored document
 * @param optimisticConcurrencyValue provider token (e.g. a Cosmos DB {@code _etag}), a string or a number
 * @param lastUpdateAt ISO time of the last store
 * @param lastProjectionInfo what produced the last store
 * @param additionalProperties unknown keys, preserved
 */
public record BoosterMetadata(
    long version,
    int schemaVersion,
    Object optimisticConcurrencyValue,
    String lastUpdateAt,
    LastProjectionInfo lastProjectionInfo,
    @JsonAnySetter @JsonAnyGetter Map<String, Object> additionalProperties) {

  /** Canonical constructor; copies the additional properties. */
  public BoosterMetadata {
    additionalProperties = additionalProperties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(additionalProperties);
  }

  /**
   * @param version the version
   * @param schemaVersion the schema version
   * @return metadata with only the required fields
   */
  public static BoosterMetadata of(long version, int schemaVersion) {
    return new BoosterMetadata(version, schemaVersion, null, null, null, null);
  }

  /**
   * Which projection produced the stored read model. {@code entityName} and {@code entityUpdatedAt} may be absent.
   *
   * @param entityId id of the projected entity
   * @param entityName type of the projected entity
   * @param entityUpdatedAt {@code createdAt} of the projected snapshot
   * @param projectionMethod {@code "<ReadModelClass>.<method>"}
   */
  public record LastProjectionInfo(String entityId, String entityName, String entityUpdatedAt, String projectionMethod) {}
}
