package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.helpers.JsValues.get;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.helpers.JsValues;
import com.boostercloud.framework.provider.azure.helpers.QueryHelper;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.envelope.PaginatedEntityIdResult;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Port of {@code library/events-searcher-adapter.ts}: the events search API and the paginated entity ids. */
public final class EventsSearcherAdapter {

  private EventsSearcherAdapter() {}

  /**
   * {@code searchEvents}: filter {@code { <entity or type>, createdAt?, kind: { eq: 'event' } }}, newest first,
   * {@code parameters.limit} results at most. Soft-deleted events are not excluded (quirk Q12).
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param parameters an {@code EventSearchParameters} record or a raw map
   * @return the events
   */
  @SuppressWarnings("unchecked")
  public static List<EventSearchResponse> searchEvents(CosmosClient cosmosDb, BoosterConfig config, Object parameters) {
    Logger logger = Loggers.getLogger(config, "events-searcher-adapter#searchEvents");
    logger.debug("Initiating an events search. Filters: ", parameters);

    String eventStore = config.resourceNames().eventsStore();
    Map<String, Object> timeFilterQuery = EventsSearcherBuilder.buildFiltersForByTime(
        stringOrNull(get(parameters, "from")), stringOrNull(get(parameters, "to")));
    Map<String, Object> eventFilterQuery = EventsSearcherBuilder.buildFiltersForByFilters(parameters);
    Map<String, Object> filterQuery = new LinkedHashMap<>(eventFilterQuery);
    filterQuery.putAll(timeFilterQuery);
    filterQuery.put("kind", Map.of("eq", "event"));
    Object limit = get(parameters, "limit");
    Object result = QueryHelper.search(cosmosDb, config, eventStore, filterQuery,
        limit instanceof Number number ? number.intValue() : null, null, null, Map.of("createdAt", "DESC"), null);
    List<EventSearchResponse> eventEnvelopes = EventsSearcherBuilder.resultToEventSearchResponse(
        result instanceof List<?> list ? list : ((ReadModelListResult<Object>) result).items());
    logger.debug("Events search result: ", eventEnvelopes);
    return eventEnvelopes;
  }

  /**
   * {@code searchEntitiesIds}: {@code SELECT DISTINCT c.entityID} of the non-deleted events of an entity type, paginated
   * (OFFSET/LIMIT unless the cursor has a continuation token).
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param limit page size
   * @param afterCursor the previous cursor, or {@code null}
   * @param entityTypeName the entity class name
   * @return the page (TypeScript items also carry {@code boosterMetadata}; the Java record keeps only {@code entityID})
   */
  public static PaginatedEntitiesIdsResult searchEntitiesIds(
      CosmosClient cosmosDb, BoosterConfig config, int limit, Map<String, String> afterCursor, String entityTypeName) {
    Logger logger = Loggers.getLogger(config, "events-searcher-adapter#searchEntitiesIds");
    logger.debug("Initiating a paginated events search. limit: " + limit + ", afterCursor: "
        + JsValues.stringify(afterCursor) + ", entityTypeName: " + entityTypeName);
    Map<String, Object> filterQuery = new LinkedHashMap<>();
    filterQuery.put("kind", Map.of("eq", "event"));
    filterQuery.put("entityTypeName", Map.of("eq", entityTypeName));
    filterQuery.put("deletedAt", Map.of("isDefined", false));
    String eventStore = config.resourceNames().eventsStore();

    Object result = QueryHelper.search(cosmosDb, config, eventStore, filterQuery, limit, afterCursor, true, null,
        "DISTINCT c.entityID");
    PaginatedEntitiesIdsResult page = toPaginatedEntitiesIds(result);
    logger.debug("Unique events search result", page);
    return page;
  }

  private static PaginatedEntitiesIdsResult toPaginatedEntitiesIds(Object result) {
    List<?> rows;
    Integer count = null;
    Map<String, String> cursor = null;
    if (result instanceof ReadModelListResult<?> pageResult) {
      rows = pageResult.items();
      count = pageResult.count();
      cursor = pageResult.cursor();
    } else {
      rows = result instanceof List<?> list ? list : List.of();
    }
    List<PaginatedEntityIdResult> items = new ArrayList<>();
    for (Object row : rows) {
      Object entityID = get(row, "entityID");
      items.add(new PaginatedEntityIdResult(entityID == null ? null : UUID.of(entityID.toString())));
    }
    return new PaginatedEntitiesIdsResult(items, count, cursor);
  }

  private static String stringOrNull(Object value) {
    return value == null ? null : value.toString();
  }
}
