package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.common.Groups;
import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.local.services.EventRegistry;
import com.boostercloud.framework.provider.local.store.Js;
import com.boostercloud.framework.provider.local.store.Model;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.envelope.PaginatedEntityIdResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Event search and the paginated list of entity ids. Port of {@code library/events-search-adapter.ts}.
 */
public final class EventsSearchAdapter {

  /** {@code DEFAULT_CREATED_AT_SORT_ORDER}: newest first. */
  public static final int DEFAULT_CREATED_AT_SORT_ORDER = -1;

  private EventsSearchAdapter() {}

  private static Map<String, Object> defaultKindFilter() {
    Map<String, Object> filter = new LinkedHashMap<>();
    filter.put("kind", "event");
    return filter;
  }

  /**
   * {@code searchEvents(eventRegistry, config, parameters)}: the events of an entity type (and id) or of an event type,
   * optionally between {@code from} and {@code to} (inclusive), newest first, at most {@code limit}. Soft-deleted
   * events are included (with an empty value and their {@code deletedAt}).
   *
   * @param eventRegistry the registry
   * @param config the configuration
   * @param parameters the search
   * @return the responses
   */
  public static List<EventSearchResponse> searchEvents(EventRegistry eventRegistry, BoosterConfig config,
      EventSearchParameters parameters) {
    Logger logger = Loggers.getLogger(config, "events-searcher-adapter#searchEvents");
    logger.debug("Initiating an events search. Filters: ", parameters);
    Map<String, Object> timeFilterQuery = EventsSearcherBuilder.buildFiltersForByTime(
        parameters == null ? null : parameters.from(), parameters == null ? null : parameters.to());
    Map<String, Object> eventFilterQuery = EventsSearcherBuilder.buildFiltersForByFilters(parameters);
    Map<String, Object> filterQuery = new LinkedHashMap<>(eventFilterQuery);
    filterQuery.putAll(timeFilterQuery);
    filterQuery.putAll(defaultKindFilter());
    List<Map<String, Object>> result =
        eventRegistry.query(filterQuery, DEFAULT_CREATED_AT_SORT_ORDER, parameters.limit(), null);
    List<EventSearchResponse> eventsSearchResponses = EventsSearcherBuilder.resultToEventSearchResponse(result);
    logger.debug("Events search result: ", eventsSearchResponses);
    return eventsSearchResponses;
  }

  /**
   * {@code searchEntitiesIds(eventRegistry, config, limit, afterCursor, entityTypeName)}: a page of the distinct ids of
   * an entity type, ordered by their most recent (non-deleted) event, most recent first. The cursor is an offset in
   * that list: {@code { id: String((limit || 1) + offset) }}, always present (TypeScript's offset pagination,
   * including its {@code parseInt} handling of the cursor).
   *
   * @param eventRegistry the registry
   * @param config the configuration
   * @param limit the page size
   * @param afterCursor the cursor of the previous page, or {@code null}
   * @param entityTypeName the entity name
   * @return the page
   */
  public static PaginatedEntitiesIdsResult searchEntitiesIds(EventRegistry eventRegistry, BoosterConfig config,
      int limit, Map<String, String> afterCursor, String entityTypeName) {
    Logger logger = Loggers.getLogger(config, "events-searcher-adapter#searchEntitiesIds");
    logger.debug("Initiating a paginated events search. limit: " + limit + ", afterCursor: "
        + (afterCursor == null ? "undefined" : BoosterJson.toJson(afterCursor)) + ", entityTypeName: " + entityTypeName);
    Map<String, Object> filterQuery = defaultKindFilter();
    filterQuery.put("entityTypeName", entityTypeName);
    filterQuery.put("deletedAt", Map.of("$exists", false));
    Map<String, Object> projection = new LinkedHashMap<>();
    projection.put("entityID", 1);
    List<Map<String, Object>> result =
        eventRegistry.query(filterQuery, DEFAULT_CREATED_AT_SORT_ORDER, null, projection);

    // NeDB doesn't support DISTINCT, so the results are paginated manually
    List<Object> entitiesIds = new ArrayList<>();
    if (result != null) {
      for (Map<String, Object> document : result) {
        entitiesIds.add(document.containsKey("entityID") ? document.get("entityID") : Model.UNDEFINED);
      }
    }
    List<Object> uniqueResult = Groups.unique(entitiesIds);
    Object cursorId = afterCursor == null ? null : afterCursor.get("id");
    double skipId = Js.truthy(cursorId) ? Js.parseInt(cursorId) : 0;
    List<Object> paginated = Js.slice(uniqueResult, skipId, skipId + limit);
    List<PaginatedEntityIdResult> paginatedResult = new ArrayList<>(paginated.size());
    for (Object entityId : paginated) {
      paginatedResult.add(new PaginatedEntityIdResult(
          entityId == null || entityId == Model.UNDEFINED ? null : UUID.of(Js.toJsString(entityId))));
    }
    logger.debug("Unique events search result", paginatedResult);
    Map<String, String> cursor = new LinkedHashMap<>();
    cursor.put("id", Js.toJsString((limit != 0 ? limit : 1) + skipId));
    return new PaginatedEntitiesIdsResult(paginatedResult, paginatedResult.size(), cursor);
  }
}
