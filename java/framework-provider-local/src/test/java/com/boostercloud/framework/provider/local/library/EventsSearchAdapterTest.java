package com.boostercloud.framework.provider.local.library;

import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.local.services.EventRegistry;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.EventParametersFilterByEntity;
import com.boostercloud.framework.types.envelope.EventParametersFilterByType;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Port of {@code test/library/events-search-adapter.test.ts}, plus the uncovered search paths. */
class EventsSearchAdapterTest {

  private BoosterConfig mockConfig;
  private EventRegistry mockEventRegistry;

  @BeforeEach
  void setUp() {
    mockConfig = new BoosterConfig("test");
    mockConfig.logger = mock(com.boostercloud.framework.types.Logger.class);
    mockEventRegistry = mock(EventRegistry.class);
    when(mockEventRegistry.query(any(), any(), any(), any())).thenReturn(null);
  }

  @Test
  void generatesTheQueryForEntityTypeNameLimitAndAfterCursor() {
    PaginatedEntitiesIdsResult result =
        EventsSearchAdapter.searchEntitiesIds(mockEventRegistry, mockConfig, 1, Map.of("id", "1"), "entity");
    verify(mockEventRegistry).query(map("kind", "event", "entityTypeName", "entity", "deletedAt", map("$exists", false)),
        -1, null, map("entityID", 1));
    assertThat(result.items()).isEmpty();
    assertThat(result.count()).isZero();
    assertThat(result.cursor()).isEqualTo(Map.of("id", "2"));
  }

  @Test
  void generatesTheQueryForEntityTypeNameAndLimit() {
    EventsSearchAdapter.searchEntitiesIds(mockEventRegistry, mockConfig, 1, null, "entity");
    verify(mockEventRegistry).query(map("kind", "event", "entityTypeName", "entity", "deletedAt", map("$exists", false)),
        -1, null, map("entityID", 1));
  }

  @Test
  void paginatesTheDistinctIdsByRecency() {
    when(mockEventRegistry.query(any(), any(), any(), any())).thenReturn(List.of(
        map("entityID", "c", "_id", "1"), map("entityID", "a", "_id", "2"), map("entityID", "c", "_id", "3"),
        map("entityID", "b", "_id", "4")));
    PaginatedEntitiesIdsResult page = EventsSearchAdapter.searchEntitiesIds(mockEventRegistry, mockConfig, 2,
        Map.of("id", "1"), "entity");
    assertThat(page.items()).extracting(item -> item.entityID().value()).containsExactly("a", "b");
    assertThat(page.count()).isEqualTo(2);
    assertThat(page.cursor()).isEqualTo(Map.of("id", "3"));
    assertThat(EventsSearchAdapter.searchEntitiesIds(mockEventRegistry, mockConfig, 0, null, "entity").cursor())
        .isEqualTo(Map.of("id", "1"));
    assertThat(EventsSearchAdapter.searchEntitiesIds(mockEventRegistry, mockConfig, 5, Map.of("id", "2x"), "entity")
        .items()).extracting(item -> item.entityID().value()).containsExactly("b");
  }

  @Test
  void searchEventsBuildsTheQueryAndSortsNewestFirst() {
    when(mockEventRegistry.query(any(), any(), any(), any())).thenReturn(List.of(map("typeName", "T",
        "entityTypeName", "E", "entityID", "e1", "requestID", "r", "createdAt", "2026-01-01T00:00:00.000Z",
        "value", map("a", 1), "currentUser", map("username", "u", "roles", List.of("R"), "claims", map()))));
    var result = EventsSearchAdapter.searchEvents(mockEventRegistry, mockConfig,
        new EventParametersFilterByEntity("E", "e1", "2026-01-01T00:00:00.000Z", null, 5));
    verify(mockEventRegistry).query(map("entityTypeName", "E", "entityID", "e1", "createdAt",
        map("$gte", "2026-01-01T00:00:00.000Z"), "kind", "event"), -1, 5, null);
    assertThat(result).singleElement().satisfies(response -> {
      assertThat(response.type()).isEqualTo("T");
      assertThat(response.entity()).isEqualTo("E");
      assertThat(response.user().username()).isEqualTo("u");
      assertThat(response.deletedAt()).isNull();
    });
    EventsSearchAdapter.searchEvents(mockEventRegistry, mockConfig,
        new EventParametersFilterByType("T", null, "2027-01-01T00:00:00.000Z", null));
    verify(mockEventRegistry).query(map("typeName", "T", "createdAt", map("$lte", "2027-01-01T00:00:00.000Z"),
        "kind", "event"), -1, null, null);
  }
}
