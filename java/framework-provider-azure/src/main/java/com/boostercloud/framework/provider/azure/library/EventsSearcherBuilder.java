package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.helpers.JsValues.get;
import static com.boostercloud.framework.provider.azure.helpers.JsValues.has;
import static com.boostercloud.framework.provider.azure.helpers.JsValues.truthy;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.envelope.EventParametersFilterByEntity;
import com.boostercloud.framework.types.envelope.EventParametersFilterByType;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code library/events-searcher-builder.ts}: the filters of an events search and the mapping of the results.
 */
public final class EventsSearcherBuilder {

  private EventsSearcherBuilder() {}

  /**
   * {@code buildFiltersForByTime}.
   *
   * @param fromValue lower bound (inclusive), or {@code null}
   * @param toValue upper bound (inclusive), or {@code null}
   * @return {@code { createdAt: { gte, lte } }} with the bounds present (truthiness), or {@code {}}
   */
  public static Map<String, Object> buildFiltersForByTime(String fromValue, String toValue) {
    Map<String, Object> createdAt = new LinkedHashMap<>();
    if (truthy(fromValue) && truthy(toValue)) {
      createdAt.put("gte", fromValue);
      createdAt.put("lte", toValue);
    } else if (truthy(fromValue)) {
      createdAt.put("gte", fromValue);
    } else if (truthy(toValue)) {
      createdAt.put("lte", toValue);
    } else {
      return new LinkedHashMap<>();
    }
    Map<String, Object> filter = new LinkedHashMap<>();
    filter.put("createdAt", createdAt);
    return filter;
  }

  /**
   * {@code buildFiltersForByFilters}: by entity and id ({@code entityTypeName_entityID_kind}), by entity
   * ({@code entityTypeName}) or by type ({@code typeName}). Accepts the typed parameters or a raw map; with a raw map
   * that has both {@code entity} and {@code type}, the entity wins (as in TypeScript).
   *
   * @param parameters an {@code EventSearchParameters} record or a raw map
   * @return the filter
   * @throws IllegalStateException when it is neither a search by entity nor by type
   */
  public static Map<String, Object> buildFiltersForByFilters(Object parameters) {
    if (parameters instanceof EventParametersFilterByEntity byEntity) {
      return byEntity(byEntity.entity(), byEntity.entityID());
    }
    if (parameters instanceof EventParametersFilterByType byType) {
      return eq("typeName", byType.type());
    }
    if (parameters instanceof Map<?, ?> && has(parameters, "entity")) {
      Object entity = get(parameters, "entity");
      Object entityID = get(parameters, "entityID");
      return byEntity(entity == null ? null : entity.toString(), entityID);
    }
    if (parameters instanceof Map<?, ?> && has(parameters, "type")) {
      return eq("typeName", get(parameters, "type"));
    }
    throw new IllegalStateException(
        "Invalid search event query. It is neither an search by \"entity\" nor a search by \"type\"");
  }

  private static Map<String, Object> byEntity(String entity, Object entityID) {
    if (truthy(entityID)) {
      return eq("entityTypeName_entityID_kind", PartitionKeys.partitionKey(entity, entityID, "event"));
    }
    return eq("entityTypeName", entity);
  }

  private static Map<String, Object> eq(String property, Object value) {
    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("eq", value);
    Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(property, operation);
    return filter;
  }

  /**
   * {@code resultToEventSearchResponse}: {@code type ← typeName}, {@code entity ← entityTypeName},
   * {@code entityID}, {@code requestID}, {@code user ← currentUser}, {@code createdAt}, {@code value},
   * {@code deletedAt}.
   *
   * @param result the documents, or {@code null}
   * @return the responses ({@code []} for {@code null}/empty)
   */
  public static List<EventSearchResponse> resultToEventSearchResponse(List<?> result) {
    if (result == null || result.isEmpty()) {
      return new ArrayList<>();
    }
    List<EventSearchResponse> responses = new ArrayList<>();
    for (Object item : result) {
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("type", get(item, "typeName"));
      response.put("entity", get(item, "entityTypeName"));
      response.put("entityID", get(item, "entityID"));
      response.put("requestID", get(item, "requestID"));
      response.put("user", get(item, "currentUser"));
      response.put("createdAt", get(item, "createdAt"));
      response.put("value", get(item, "value"));
      response.put("deletedAt", get(item, "deletedAt"));
      responses.add(BoosterJson.convert(response, EventSearchResponse.class));
    }
    return responses;
  }
}
