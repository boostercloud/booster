package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.EventParametersFilterByEntity;
import com.boostercloud.framework.types.envelope.EventParametersFilterByType;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the queries of the event search and maps the results. Port of {@code library/events-searcher-builder.ts}.
 */
public final class EventsSearcherBuilder {

  private EventsSearcherBuilder() {}

  /**
   * {@code buildFiltersForByTime(fromValue?, toValue?)}: inclusive {@code createdAt} bounds (string comparison).
   *
   * @param fromValue the lower bound, or {@code null}/empty
   * @param toValue the upper bound, or {@code null}/empty
   * @return {@code { createdAt: { $gte, $lte } }}, one of its halves, or {@code {}}
   */
  public static Map<String, Object> buildFiltersForByTime(String fromValue, String toValue) {
    boolean hasFrom = fromValue != null && !fromValue.isEmpty();
    boolean hasTo = toValue != null && !toValue.isEmpty();
    Map<String, Object> bounds = new LinkedHashMap<>();
    if (hasFrom) {
      bounds.put("$gte", fromValue);
    }
    if (hasTo) {
      bounds.put("$lte", toValue);
    }
    Map<String, Object> filter = new LinkedHashMap<>();
    if (!bounds.isEmpty()) {
      filter.put("createdAt", bounds);
    }
    return filter;
  }

  /**
   * {@code buildFiltersForByFilters(filters)}: {@code { entityTypeName, entityID? }} for a search by entity,
   * {@code { typeName }} for a search by type.
   *
   * @param filters the search parameters
   * @return the query fields
   * @throws IllegalArgumentException {@code Invalid search event query. It is neither an search by "entity" nor a
   *     search by "type"} when the parameters are neither
   */
  public static Map<String, Object> buildFiltersForByFilters(EventSearchParameters filters) {
    Map<String, Object> query = new LinkedHashMap<>();
    if (filters instanceof EventParametersFilterByEntity byEntity) {
      query.put("entityTypeName", byEntity.entity());
      if (byEntity.entityID() != null && !byEntity.entityID().isEmpty()) {
        query.put("entityID", byEntity.entityID());
      }
      return query;
    }
    if (filters instanceof EventParametersFilterByType byType) {
      query.put("typeName", byType.type());
      return query;
    }
    throw new IllegalArgumentException(
        "Invalid search event query. It is neither an search by \"entity\" nor a search by \"type\"");
  }

  /**
   * {@code resultToEventSearchResponse(result)}: maps stored events to search responses
   * ({@code type <- typeName}, {@code entity <- entityTypeName}, {@code user <- currentUser}).
   *
   * @param result stored event documents (or envelopes), or {@code null}
   * @return the responses
   */
  public static List<EventSearchResponse> resultToEventSearchResponse(List<?> result) {
    if (result == null || result.isEmpty()) {
      return List.of();
    }
    List<EventSearchResponse> responses = new ArrayList<>(result.size());
    for (Object item : result) {
      if (item instanceof EventEnvelope envelope) {
        responses.add(EventSearchResponse.fromEventEnvelope(envelope));
      } else if (item instanceof Map<?, ?> document) {
        responses.add(new EventSearchResponse(
            stringOrNull(document.get("typeName")),
            stringOrNull(document.get("entityTypeName")),
            document.get("entityID") == null ? null : UUID.of(String.valueOf(document.get("entityID"))),
            document.get("requestID") == null ? null : UUID.of(String.valueOf(document.get("requestID"))),
            document.get("currentUser") == null ? null : BoosterJson.convert(document.get("currentUser"),
                UserEnvelope.class),
            stringOrNull(document.get("createdAt")),
            document.get("value"),
            stringOrNull(document.get("deletedAt"))));
      }
    }
    return responses;
  }

  private static String stringOrNull(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
