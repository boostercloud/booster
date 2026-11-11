package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.boostercloud.framework.provider.azure.cosmos.SqlParameter;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.provider.azure.support.CosmosMocks;
import com.boostercloud.framework.provider.azure.support.EventHelper;
import com.boostercloud.framework.types.BoosterConfig;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tests of {@code event-delete-adapter.ts} (no TypeScript tests exist). */
class EventDeleteAdapterTest {

  private BoosterConfig config;
  private CosmosMocks cosmos;
  private Logger logger;

  @BeforeEach
  void setUp() {
    cosmos = new CosmosMocks();
    config = new BoosterConfig("test");
    logger = mock(Logger.class);
    config.logger = logger;
    Dates.useClock(Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC));
  }

  @AfterEach
  void resetClock() {
    Dates.resetClock();
  }

  @Test
  void findsDeletableEventsWithTheEventFilter() {
    Map<String, Object> document = EventHelper.withSystemProperties(EventHelper.nonPersisted("Cart", "42")
        .persisted("2026-11-10T09:15:02.123Z").toBuilder().id("doc-1").build());
    cosmos.returnRows(List.of(document));

    List<EventEnvelope> events = EventDeleteAdapter.findDeletableEvent(cosmos.client, config,
        new EventDeleteParameters("Cart", UUID.of("42"), "2026-11-10T09:15:02.123Z"));

    assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
        "SELECT * FROM c WHERE c[\"entityTypeName_entityID_kind\"] = @entityTypeName_entityID_kind_0 AND "
            + "c[\"createdAt\"] = @createdAt_0 AND c[\"kind\"] = @kind_0 AND NOT IS_DEFINED(c[\"deletedAt\"])",
        List.of(SqlParameter.of("@entityTypeName_entityID_kind_0", "Cart-42-event"),
            SqlParameter.of("@createdAt_0", "2026-11-10T09:15:02.123Z"), SqlParameter.of("@kind_0", "event"))));
    assertThat(events).extracting(EventEnvelope::id).containsExactly("doc-1");
    verify(logger).debug("[Booster]|events-delete-adapter#findDeletableEvent: ",
        "Initiating a deletable event search for {\"entityTypeName\":\"Cart\",\"entityID\":\"42\","
            + "\"createdAt\":\"2026-11-10T09:15:02.123Z\"}");
  }

  @Test
  void findsDeletableSnapshotsWithTheSnapshotFilterOrder() {
    EventDeleteAdapter.findDeletableSnapshot(cosmos.client, config,
        new SnapshotDeleteParameters(UUID.of("42"), "Cart", "t"));

    assertThat(cosmos.querySpec().query()).isEqualTo(
        "SELECT * FROM c WHERE c[\"entityTypeName_entityID_kind\"] = @entityTypeName_entityID_kind_0 AND "
            + "c[\"kind\"] = @kind_0 AND c[\"createdAt\"] = @createdAt_0 AND NOT IS_DEFINED(c[\"deletedAt\"])");
  }

  @Test
  void softDeletesEventsByReplacingTheDocumentTheyWereReadFrom() {
    Map<String, Object> document = EventHelper.withSystemProperties(EventHelper.nonPersisted("Cart", "42")
        .persisted("2026-11-10T09:15:02.123Z").toBuilder().id("doc-1").build());
    cosmos.returnRows(List.of(document));
    List<EventEnvelope> events = EventDeleteAdapter.findDeletableEvent(cosmos.client, config,
        new EventDeleteParameters("Cart", UUID.of("42"), "2026-11-10T09:15:02.123Z"));

    EventDeleteAdapter.deleteEvent(cosmos.client, config, events);

    verify(cosmos.container).item("doc-1", "Cart-42-event");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> replaced = ArgumentCaptor.forClass(Map.class);
    verify(cosmos.item).replace(replaced.capture());
    Map<String, Object> expected = new LinkedHashMap<>(document);
    expected.put("boosterMetadata", obj("optimisticConcurrencyValue", document.get("_etag")));
    expected.put("deletedAt", "2026-11-10T10:00:00.000Z");
    expected.put("value", obj());
    assertThat(replaced.getValue()).isEqualTo(expected);
  }

  @Test
  void eventsThisProviderDidNotReadAreRebuiltWithTheirPartitionKey() {
    EventEnvelope event = EventHelper.nonPersisted("Cart", "42").persisted("t").toBuilder().id("doc-9").build();

    EventDeleteAdapter.deleteEvent(cosmos.client, config, List.of(event));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> replaced = ArgumentCaptor.forClass(Map.class);
    verify(cosmos.item).replace(replaced.capture());
    assertThat(replaced.getValue()).containsEntry("entityTypeName_entityID_kind", "Cart-42-event")
        .containsEntry("deletedAt", "2026-11-10T10:00:00.000Z").containsEntry("value", obj()).containsEntry("id", "doc-9");
  }

  @Test
  void hardDeletesSnapshots() {
    EntitySnapshotEnvelope snapshot = EntitySnapshotEnvelope.builder().id("snap-1").entityTypeName("Cart")
        .entityID(UUID.of("42")).build();

    EventDeleteAdapter.deleteSnapshot(cosmos.client, config, List.of(snapshot));

    verify(cosmos.container).item("snap-1", "Cart-42-snapshot");
    verify(cosmos.item).delete();
    verify(cosmos.item, never()).replace(any());
  }

  @Test
  void nothingToDeleteOnlyWarns() {
    EventDeleteAdapter.deleteEvent(cosmos.client, config, List.of());
    EventDeleteAdapter.deleteSnapshot(cosmos.client, config, null);

    verify(logger).warn("[Booster]|events-delete-adapter#deleteEvent: ", "Could not find events to delete");
    verify(logger).warn("[Booster]|events-delete-adapter#deleteSnapshot: ", "Could not find snapshot to delete");
    verify(cosmos.container, never()).item(any(), any());
  }
}
