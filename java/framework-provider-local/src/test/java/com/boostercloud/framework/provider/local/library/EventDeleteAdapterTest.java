package com.boostercloud.framework.provider.local.library;

import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.local.Mocks;
import com.boostercloud.framework.provider.local.services.EventRegistry;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.SnapshotDeleteParameters;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests of {@code event-delete-adapter.ts} (not covered by the TypeScript tests). */
class EventDeleteAdapterTest {

  private BoosterConfig config;
  private Logger logger;
  private EventRegistry registry;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    logger = mock(Logger.class);
    config.logger = logger;
    registry = mock(EventRegistry.class);
  }

  @AfterEach
  void tearDown() {
    Dates.resetClock();
  }

  @Test
  void findDeletableEventQueriesTheExactEventAndExposesTheDatabaseId() {
    EventEnvelope event = Mocks.event();
    Map<String, Object> document = BoosterJson.toRawMap(event);
    document.put("_id", "db1");
    when(registry.query(any())).thenReturn(List.of(document));
    EventDeleteParameters parameters = new EventDeleteParameters("Cart", UUID.of("c1"), "2026-01-01T00:00:00.000Z");
    List<EventEnvelope> result = EventDeleteAdapter.findDeletableEvent(registry, config, parameters);
    verify(registry).query(map("entityID", "c1", "entityTypeName", "Cart", "createdAt", "2026-01-01T00:00:00.000Z",
        "kind", "event", "deletedAt", map("$exists", false)));
    assertThat(result).containsExactly(event.toBuilder().id("db1").build());
    String parametersJson = "{\"entityTypeName\":\"Cart\",\"entityID\":\"c1\",\"createdAt\":\"2026-01-01T00:00:00.000Z\"}";
    verify(logger).debug("[Booster]|events-delete-adapter#findDeletableEvent: ",
        "Initiating a deletable event search for " + parametersJson);
    verify(logger).debug("[Booster]|events-delete-adapter#findDeletableEvent: ",
        "Finished deletable event search for " + parametersJson);
  }

  @Test
  void findDeletableSnapshotQueriesTheSnapshotsOfThatEvent() {
    EntitySnapshotEnvelope snapshot = Mocks.snapshot("Cart", "c1");
    Map<String, Object> document = BoosterJson.toRawMap(snapshot);
    document.put("_id", "s1");
    when(registry.query(any())).thenReturn(List.of(document));
    List<EntitySnapshotEnvelope> result = EventDeleteAdapter.findDeletableSnapshot(registry, config,
        new SnapshotDeleteParameters(UUID.of("c1"), "Cart", "2026-01-01T00:00:00.000Z"));
    verify(registry).query(map("entityTypeName", "Cart", "entityID", "c1", "kind", "snapshot", "createdAt",
        "2026-01-01T00:00:00.000Z", "deletedAt", map("$exists", false)));
    assertThat(result).containsExactly(snapshot.toBuilder().id("s1").build());
  }

  @Test
  void deleteEventSoftDeletesEachEventSequentially() {
    Dates.useClock(Clock.fixed(Instant.parse("2026-02-01T00:00:00.000Z"), ZoneOffset.UTC));
    EventEnvelope first = Mocks.event().toBuilder().id("a").build();
    EventEnvelope second = Mocks.event().toBuilder().id("b").build();
    EventDeleteAdapter.deleteEvent(registry, config, List.of(first, second));
    org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(registry);
    inOrder.verify(registry).replaceOrDeleteItem("a", BoosterJson.toRawMap(first.toBuilder()
        .deletedAt("2026-02-01T00:00:00.000Z").value(map()).build()));
    inOrder.verify(registry).replaceOrDeleteItem(org.mockito.ArgumentMatchers.eq("b"), any());
    verify(logger).debug("[Booster]|events-delete-adapter#deleteEvent: ",
        "Finished event delete for " + BoosterJson.toJson(List.of(first, second)));
  }

  @Test
  void deleteEventWithoutEventsOnlyWarns() {
    EventDeleteAdapter.deleteEvent(registry, config, List.of());
    EventDeleteAdapter.deleteEvent(registry, config, null);
    verify(registry, never()).replaceOrDeleteItem(anyString(), any());
    verify(logger, org.mockito.Mockito.times(2)).warn("[Booster]|events-delete-adapter#deleteEvent: ",
        "Could not find events to delete");
  }

  @Test
  void deleteSnapshotRemovesEachSnapshot() {
    EventDeleteAdapter.deleteSnapshot(registry, config, List.of(Mocks.snapshot(null, null).toBuilder().id("s1").build()));
    verify(registry).replaceOrDeleteItem("s1");
    EventDeleteAdapter.deleteSnapshot(registry, config, List.of());
    verify(logger).warn("[Booster]|events-delete-adapter#deleteSnapshot: ", "Could not find snapshot to delete");
  }
}
