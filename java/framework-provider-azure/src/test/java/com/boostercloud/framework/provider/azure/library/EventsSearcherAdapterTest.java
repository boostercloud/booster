package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.helpers.QueryHelper;
import com.boostercloud.framework.provider.azure.support.CosmosMocks;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventParametersFilterByEntity;
import com.boostercloud.framework.types.envelope.EventParametersFilterByType;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.envelope.PaginatedEntityIdResult;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/** Port of {@code test/library/events-searcher-adapter.test.ts} ({@code query-helper.search} stubbed). */
class EventsSearcherAdapterTest {

  private static final String EVENT_STORE_NAME = "new-booster-app-app-events-store";

  private BoosterConfig config;
  private CosmosClient client;
  private MockedStatic<QueryHelper> mockSearch;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    client = new CosmosMocks().client;
    mockSearch = mockStatic(QueryHelper.class);
    mockSearch.when(() -> QueryHelper.search(any(), any(), anyString(), anyMap(), any(), any(), any(), any(), any()))
        .thenReturn(List.of());
  }

  @AfterEach
  void tearDown() {
    mockSearch.close();
  }

  /** The 9 arguments of the call, the filter keys in order. */
  private List<Object> searchCall() {
    ArgumentCaptor<Map<String, ?>> filters = mapCaptor();
    ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<Map<String, ?>> afterCursor = mapCaptor();
    ArgumentCaptor<Boolean> paginated = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<Map<String, ?>> order = mapCaptor();
    ArgumentCaptor<Object> projections = ArgumentCaptor.forClass(Object.class);
    mockSearch.verify(() -> QueryHelper.search(org.mockito.ArgumentMatchers.eq(client),
        org.mockito.ArgumentMatchers.eq(config), org.mockito.ArgumentMatchers.eq(EVENT_STORE_NAME), filters.capture(),
        limit.capture(), afterCursor.capture(), paginated.capture(), order.capture(), projections.capture()));
    List<Object> call = new ArrayList<>();
    call.add(filters.getValue());
    call.add(new ArrayList<>(filters.getValue().keySet()));
    call.add(limit.getValue());
    call.add(afterCursor.getValue());
    call.add(paginated.getValue());
    call.add(order.getValue());
    call.add(projections.getValue());
    return call;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ArgumentCaptor<Map<String, ?>> mapCaptor() {
    return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
  }

  @Nested
  class TheSearchEventsMethod {

    @Test
    void generatesFiltersForEntityEntityIdAndTimeWhenTheParametersHaveAllFields() {
      // A raw map can carry entity and type together (the typed parameters cannot): entity wins
      EventsSearcherAdapter.searchEvents(client, config,
          obj("from", "from", "to", "to", "entity", "entity", "entityID", "entityID", "type", "type"));

      List<Object> call = searchCall();
      assertThat(call.get(0)).isEqualTo(obj(
          "entityTypeName_entityID_kind", obj("eq", "entity-entityID-event"),
          "createdAt", obj("gte", "from", "lte", "to"),
          "kind", obj("eq", "event")));
      assertThat(call.get(1)).isEqualTo(List.of("entityTypeName_entityID_kind", "createdAt", "kind"));
      assertThat(call.subList(2, 7)).containsExactly(null, null, null, Map.of("createdAt", "DESC"), null);
    }

    @Test
    void generatesFiltersForEntityEntityIdAndTimeWhenTheParametersHaveAllFieldsAndLimited() {
      EventsSearcherAdapter.searchEvents(client, config,
          new EventParametersFilterByEntity("entity", "entityID", "from", "to", 3));

      List<Object> call = searchCall();
      assertThat(call.get(0)).isEqualTo(obj(
          "entityTypeName_entityID_kind", obj("eq", "entity-entityID-event"),
          "createdAt", obj("gte", "from", "lte", "to"),
          "kind", obj("eq", "event")));
      assertThat(call.get(2)).isEqualTo(3);
    }

    @Test
    void generatesFiltersForEntityAndEntityIdWhenTheParametersHaveEntityAndEntityIdFields() {
      EventsSearcherAdapter.searchEvents(client, config,
          new EventParametersFilterByEntity("entity", "entityID", null, null, null));

      assertThat(searchCall().get(0)).isEqualTo(obj(
          "entityTypeName_entityID_kind", obj("eq", "entity-entityID-event"), "kind", obj("eq", "event")));
    }

    @Test
    void generatesFiltersForTypeWhenTheParametersHaveTypeField() {
      EventsSearcherAdapter.searchEvents(client, config, new EventParametersFilterByType("type", null, null, null));

      List<Object> call = searchCall();
      assertThat(call.get(0)).isEqualTo(obj("typeName", obj("eq", "type"), "kind", obj("eq", "event")));
      assertThat(call.get(1)).isEqualTo(List.of("typeName", "kind"));
    }

    @Test
    void generatesFiltersForEntityWhenTheParametersHaveOnlyEntityField() {
      EventsSearcherAdapter.searchEvents(client, config,
          new EventParametersFilterByEntity("entity", null, null, null, null));

      assertThat(searchCall().get(0)).isEqualTo(obj("entityTypeName", obj("eq", "entity"), "kind", obj("eq", "event")));
    }

    @Test
    void onlyFromOrOnlyToProduceOneBound() {
      assertThat(EventsSearcherBuilder.buildFiltersForByTime("from", null)).isEqualTo(obj("createdAt", obj("gte", "from")));
      assertThat(EventsSearcherBuilder.buildFiltersForByTime("", "to")).isEqualTo(obj("createdAt", obj("lte", "to")));
      assertThat(EventsSearcherBuilder.buildFiltersForByTime(null, null)).isEqualTo(obj());
    }

    @Test
    void neitherEntityNorTypeIsAnError() {
      assertThatThrownBy(() -> EventsSearcherAdapter.searchEvents(client, config, obj("from", "x")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Invalid search event query. It is neither an search by \"entity\" nor a search by \"type\"");
    }

    @Test
    void mapsTheResultsToEventSearchResponses() {
      mockSearch.when(() -> QueryHelper.search(any(), any(), anyString(), anyMap(), any(), any(), any(), any(), any()))
          .thenReturn(List.of(obj("typeName", "CartItemAdded", "entityTypeName", "Cart", "entityID", "42",
              "requestID", "r1", "currentUser", obj("username", "jane@example.com", "roles", List.of("User")),
              "createdAt", "2026-11-10T09:15:02.123Z", "value", obj("sku", "A"), "deletedAt", "2026-11-11T00:00:00.000Z",
              "_etag", "\"e\"", "boosterMetadata", obj())));

      List<EventSearchResponse> result = EventsSearcherAdapter.searchEvents(client, config,
          new EventParametersFilterByType("CartItemAdded", null, null, null));

      assertThat(result).containsExactly(new EventSearchResponse("CartItemAdded", "Cart", UUID.of("42"), UUID.of("r1"),
          UserEnvelope.builder().username("jane@example.com").roles(List.of("User")).build(),
          "2026-11-10T09:15:02.123Z", obj("sku", "A"), "2026-11-11T00:00:00.000Z"));
    }
  }

  @Nested
  class TheSearchEntitiesIdsMethod {

    @Test
    void generatesQueryForEntityTypeNameLimitAndAfterCursor() {
      EventsSearcherAdapter.searchEntitiesIds(client, config, 1, Map.of("id", "1"), "entity");

      List<Object> call = searchCall();
      assertThat(call.get(0)).isEqualTo(obj("kind", obj("eq", "event"), "entityTypeName", obj("eq", "entity"),
          "deletedAt", obj("isDefined", false)));
      assertThat(call.get(1)).isEqualTo(List.of("kind", "entityTypeName", "deletedAt"));
      assertThat(call.subList(2, 7)).containsExactly(1, Map.of("id", "1"), true, null, "DISTINCT c.entityID");
    }

    @Test
    void generatesQueryForEntityTypeNameAndLimit() {
      EventsSearcherAdapter.searchEntitiesIds(client, config, 1, null, "entity");

      List<Object> call = searchCall();
      assertThat(call.subList(2, 7)).containsExactly(1, null, true, null, "DISTINCT c.entityID");
    }

    @Test
    void returnsTheDistinctIdsPage() {
      mockSearch.when(() -> QueryHelper.search(any(), any(), anyString(), anyMap(), anyInt(), any(), anyBoolean(), any(),
          any())).thenReturn(new ReadModelListResult<>(
              List.of(obj("entityID", "1", "boosterMetadata", obj()), obj("entityID", "2", "boosterMetadata", obj())), 2,
              Map.of("id", "2")));

      PaginatedEntitiesIdsResult result = EventsSearcherAdapter.searchEntitiesIds(client, config, 2, null, "Cart");

      assertThat(result).isEqualTo(new PaginatedEntitiesIdsResult(
          List.of(new PaginatedEntityIdResult(UUID.of("1")), new PaginatedEntityIdResult(UUID.of("2"))), 2,
          Map.of("id", "2")));
    }
  }
}
