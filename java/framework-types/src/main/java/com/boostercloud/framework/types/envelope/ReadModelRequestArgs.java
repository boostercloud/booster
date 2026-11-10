package com.boostercloud.framework.types.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

/**
 * Arguments of the GraphQL read model search resolvers. Port of {@code ReadModelRequestArgs} (note the singular
 * {@code filter}).
 *
 * @param filter the filter, optional
 * @param sortBy the sort, optional
 * @param limit maximum number of results, optional
 * @param afterCursor opaque provider cursor, optional
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"filter", "sortBy", "limit", "afterCursor"})
public record ReadModelRequestArgs(
    Map<String, Object> filter,
    Map<String, Object> sortBy,
    Integer limit,
    Object afterCursor) {
}
