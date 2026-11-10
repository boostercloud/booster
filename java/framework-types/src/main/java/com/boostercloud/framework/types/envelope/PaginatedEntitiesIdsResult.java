package com.boostercloud.framework.types.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;

/**
 * A page of entity ids. Port of {@code PaginatedEntitiesIdsResult}.
 *
 * @param items the ids
 * @param count number of items, optional
 * @param cursor opaque provider cursor to pass back as {@code afterCursor}, optional
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"items", "count", "cursor"})
public record PaginatedEntitiesIdsResult(
    List<PaginatedEntityIdResult> items,
    Integer count,
    Map<String, String> cursor) {

  /** Canonical constructor; {@code null} items become empty. */
  public PaginatedEntitiesIdsResult {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
