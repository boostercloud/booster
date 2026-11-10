package com.boostercloud.framework.types.envelope;

import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One entity id of a paginated listing. Port of {@code PaginatedEntityIdResult}.
 *
 * @param entityID the entity id
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record PaginatedEntityIdResult(
    UUID entityID) {
}
