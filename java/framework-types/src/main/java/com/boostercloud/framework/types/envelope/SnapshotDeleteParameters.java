package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Identifies a snapshot to delete. Port of {@code SnapshotDeleteParameters}.
 *
 * @param entityID the entity id
 * @param entityTypeName the entity class name
 * @param createdAt ISO creation time of the snapshot
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"entityID", "entityTypeName", "createdAt"})
public record SnapshotDeleteParameters(
    UUID entityID,
    String entityTypeName,
    String createdAt) {
}
