package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Identifies an event to delete. Port of {@code EventDeleteParameters} (TypeScript types {@code entityID} as
 * {@code string}; Booster's {@link UUID} serializes identically).
 *
 * @param entityTypeName the entity class name
 * @param entityID the entity id
 * @param createdAt ISO creation time of the event
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"entityTypeName", "entityID", "createdAt"})
public record EventDeleteParameters(
    String entityTypeName,
    UUID entityID,
    String createdAt) {

  /**
   * @return the same identification, as snapshot delete parameters
   */
  public SnapshotDeleteParameters toSnapshotDeleteParameters() {
    return new SnapshotDeleteParameters(entityID, entityTypeName, createdAt);
  }
}
