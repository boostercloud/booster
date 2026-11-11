package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.azure.cosmos.CosmosException;
import com.boostercloud.framework.provider.azure.cosmos.FeedOptions;
import com.boostercloud.framework.provider.azure.cosmos.FeedResponse;
import com.boostercloud.framework.provider.azure.cosmos.SqlParameter;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.provider.azure.support.CosmosMocks;
import com.boostercloud.framework.provider.azure.support.EventHelper;
import com.boostercloud.framework.provider.azure.types.AzureCosmosDBFunctionInput;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Port of {@code test/library/events-adapter.test.ts}, plus the behaviour the TypeScript tests leave uncovered. */
class EventsAdapterTest {

  private BoosterConfig config;
  private CosmosMocks cosmos;
  private List<EventEnvelope> mockEvents;
  private String mockEntityName;
  private UUID mockEntityId;
  private Logger logger;

  @BeforeEach
  void setUp() {
    cosmos = new CosmosMocks();
    config = new BoosterConfig("test");
    logger = mock(Logger.class);
    config.logger = logger;
    mockEntityName = "Entity" + System.nanoTime();
    mockEntityId = UUID.generate();
    mockEvents = EventHelper.createMockEventEnvelopes(2);
  }

  @AfterEach
  void resetClock() {
    Dates.resetClock();
  }

  private SqlQuerySpec singlePartitionQuery(Object partitionKey) {
    ArgumentCaptor<SqlQuerySpec> spec = ArgumentCaptor.forClass(SqlQuerySpec.class);
    verify(cosmos.items).query(spec.capture(), eq(FeedOptions.forPartition(partitionKey)));
    return spec.getValue();
  }

  @Nested
  class TheRawEventsToEnvelopesMethod {

    @Test
    void generatesEnvelopesCorrectlyFromACosmosDbEvent() {
      List<Object> expectedDocuments = List.of(
          EventHelper.withSystemProperties(mockEvents.get(0)), EventHelper.withSystemProperties(mockEvents.get(1)));
      AzureCosmosDBFunctionInput cosmosDbMessage =
          new AzureCosmosDBFunctionInput(expectedDocuments, AzureInvocationContext.of("invocation"));

      List<Object> gotDocuments = EventsAdapter.rawEventsToDocuments(cosmosDbMessage);
      List<EventEnvelope> gotEnvelopes = EventsAdapter.rawEventsToEnvelopes(cosmosDbMessage);

      assertThat(gotDocuments).isSameAs(expectedDocuments);
      assertThat(gotEnvelopes).containsExactlyElementsOf(mockEvents);
    }

    @Test
    void acceptsRawMapsAndPlainArrays() {
      List<Object> documents = List.of(EventHelper.withSystemProperties(mockEvents.getFirst()));

      assertThat(EventsAdapter.rawEventsToEnvelopes(Map.of("documents", documents, "context", Map.of())))
          .containsExactly(mockEvents.getFirst());
      assertThat(EventsAdapter.rawEventsToEnvelopes(documents)).containsExactly(mockEvents.getFirst());
    }

    @Test
    void keepsOnlyEventDocumentsBecauseJavaEnvelopesCannotCarryTheKind() {
      Map<String, Object> event = EventHelper.withSystemProperties(mockEvents.getFirst());
      Map<String, Object> snapshot = obj("kind", "snapshot", "entityTypeName", "Cart", "entityID", "1");
      Map<String, Object> deleted = obj("kind", "event", "entityTypeName", "Cart", "entityID", "2",
          "deletedAt", "2025-01-01T00:00:00.000Z", "value", obj());

      List<EventEnvelope> envelopes = EventsAdapter.rawEventsToEnvelopes(List.of(snapshot, event, deleted));

      assertThat(envelopes).hasSize(2);
      assertThat(envelopes.get(1).isDeleted()).isTrue();
    }

    @Test
    void rejectsOtherInputs() {
      assertThatThrownBy(() -> EventsAdapter.rawEventsToEnvelopes(Map.of("documents", "x", "context", Map.of())))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Invalid input type for rawEventsToEnvelopes: expected AzureCosmosDBFunctionInput or Array");
    }
  }

  @Nested
  class TheReadEntityEventsSinceMethod {

    @Test
    void queriesTheEventsTableToFindAllEventsRelatedToASpecificEntity() {
      EventsAdapter.readEntityEventsSince(cosmos.client, config, mockEntityName, mockEntityId, null);

      verify(cosmos.client).database(config.resourceNames().applicationStack());
      verify(cosmos.database).container(config.resourceNames().eventsStore());
      String partitionKey = PartitionKeys.partitionKeyForEvent(mockEntityName, mockEntityId);
      SqlQuerySpec spec = singlePartitionQuery(partitionKey);
      assertThat(spec.query()).isEqualTo(
          "SELECT * FROM c WHERE c[\"entityTypeName_entityID_kind\"] = @partitionKey "
              + "AND c[\"createdAt\"] > @fromTime AND NOT IS_DEFINED(c[\"deletedAt\"]) ORDER BY c[\"createdAt\"] ASC");
      assertThat(spec.parameters()).containsExactly(
          SqlParameter.of("@partitionKey", partitionKey),
          SqlParameter.of("@fromTime", "1970-01-01T00:00:00.000Z"));
    }

    @Test
    void usesTheGivenSinceAndTreatsTheEmptyStringAsAbsent() {
      EventsAdapter.readEntityEventsSince(cosmos.client, config, "Cart", "42", "2025-01-01T00:00:00.000Z");
      EventsAdapter.readEntityEventsSince(cosmos.client, config, "Cart", "43", "");

      ArgumentCaptor<SqlQuerySpec> spec = ArgumentCaptor.forClass(SqlQuerySpec.class);
      verify(cosmos.items, org.mockito.Mockito.times(2)).query(spec.capture(), any(FeedOptions.class));
      assertThat(spec.getAllValues().get(0).parameters().get(1).value()).isEqualTo("2025-01-01T00:00:00.000Z");
      assertThat(spec.getAllValues().get(1).parameters().get(1).value()).isEqualTo("1970-01-01T00:00:00.000Z");
    }

    @Test
    void returnsTheDocumentsAsEnvelopes() {
      cosmos.returnRows(List.of(EventHelper.withSystemProperties(mockEvents.getFirst())));

      assertThat(EventsAdapter.readEntityEventsSince(cosmos.client, config, "Cart", "42", null))
          .containsExactly(mockEvents.getFirst());
    }
  }

  @Nested
  class TheReadEntityLatestSnapshotMethod {

    @Test
    void findsTheLatestEntitySnapshot() {
      Optional<EntitySnapshotEnvelope> result =
          EventsAdapter.readEntityLatestSnapshot(cosmos.client, config, mockEntityName, mockEntityId);

      verify(cosmos.database).container(config.resourceNames().eventsStore());
      String partitionKey = PartitionKeys.partitionKeyForSnapshot(mockEntityName, mockEntityId);
      SqlQuerySpec spec = singlePartitionQuery(partitionKey);
      assertThat(spec.query()).isEqualTo("SELECT * FROM c WHERE c[\"entityTypeName_entityID_kind\"] = @partitionKey "
          + "ORDER BY c[\"createdAt\"] DESC OFFSET 0 LIMIT 1");
      assertThat(spec.parameters()).containsExactly(SqlParameter.of("@partitionKey", partitionKey));
      assertThat(result).isEmpty();
      verify(logger).debug("[Booster]|events-adapter#readEntityLatestSnapshot: ",
          "[EventsAdapter#readEntityLatestSnapshot] No snapshot found for entity " + mockEntityName + " with ID "
              + mockEntityId + ".");
    }

    @Test
    void returnsTheFirstResult() {
      cosmos.returnRows(List.of(obj("kind", "snapshot", "entityTypeName", "Cart", "entityID", "42", "typeName", "Cart",
          "value", obj("id", "42"), "createdAt", "t1", "id", "s1", "_etag", "\"e\"")));

      Optional<EntitySnapshotEnvelope> result = EventsAdapter.readEntityLatestSnapshot(cosmos.client, config, "Cart", "42");

      assertThat(result).hasValueSatisfying(snapshot -> {
        assertThat(snapshot.id()).isEqualTo("s1");
        assertThat(snapshot.value()).isEqualTo(obj("id", "42"));
      });
    }
  }

  @Nested
  class TheStoreSnapshotMethod {

    @Test
    void keepsTheDeadDuplicateGuardAndStoresTheSnapshotWithItsKeys() {
      Dates.useClock(Clock.fixed(Instant.parse("2026-11-10T09:15:03.004Z"), ZoneOffset.UTC));
      NonPersistedEntitySnapshotEnvelope snapshot = NonPersistedEntitySnapshotEnvelope.builder()
          .version(1).superKind(SuperKindType.DOMAIN).entityID(UUID.of("42")).entityTypeName("Cart").typeName("Cart")
          .value(obj("id", "42")).snapshottedEventCreatedAt("2026-11-10T09:15:02.123Z").requestID(UUID.of("r"))
          .build();

      EntitySnapshotEnvelope stored = EventsAdapter.storeSnapshot(cosmos.client, snapshot, config);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE c.partitionKey = @partitionKey AND c.sortKey = @sortKey",
          List.of(SqlParameter.of("@partitionKey", "Cart-42-snapshot"),
              SqlParameter.of("@sortKey", "2026-11-10T09:15:02.123Z"))));
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> document = ArgumentCaptor.forClass(Map.class);
      verify(cosmos.items).create(document.capture());
      assertThat(document.getValue()).containsEntry("kind", "snapshot")
          .containsEntry("entityTypeName_entityID_kind", "Cart-42-snapshot")
          .containsEntry("createdAt", "2026-11-10T09:15:02.123Z")
          .containsEntry("persistedAt", "2026-11-10T09:15:03.004Z")
          .doesNotContainKey("id");
      assertThat(stored.createdAt()).isEqualTo("2026-11-10T09:15:02.123Z");
      assertThat(stored.persistedAt()).isEqualTo("2026-11-10T09:15:03.004Z");
    }

    @Test
    void throwsWhenTheGuardFindsASnapshot() {
      cosmos.returnRows(List.of(obj("id", "x")));
      NonPersistedEntitySnapshotEnvelope snapshot = NonPersistedEntitySnapshotEnvelope.builder()
          .entityID(UUID.of("42")).entityTypeName("Cart").snapshottedEventCreatedAt("t").build();

      assertThatThrownBy(() -> EventsAdapter.storeSnapshot(cosmos.client, snapshot, config))
          .isInstanceOf(IllegalStateException.class).hasMessage("Snapshot already exists. skipping...");
    }
  }

  @Nested
  class TheStoreDispatchedEventMethod {

    @Test
    void persistsTheIdsOfTheEventEnvelopesPassedViaParameters() {
      boolean stored = EventsAdapter.storeDispatchedEvent(cosmos.client, mockEvents.getFirst(), config);

      verify(cosmos.client).database(config.resourceNames().applicationStack());
      verify(cosmos.database).container(config.resourceNames().dispatchedEventsStore());
      verify(cosmos.items).create(Map.of("eventId", mockEvents.getFirst().id()));
      assertThat(stored).isTrue();
    }

    @Test
    void returnsFalseWhenTheEventWasAlreadyDispatched() {
      when(cosmos.items.create(any())).thenThrow(CosmosException.of("Conflict", 409));

      assertThat(EventsAdapter.storeDispatchedEvent(cosmos.client, mockEvents.getFirst(), config)).isFalse();
      verify(logger).debug("[Booster]|events-adapter#storeDispatchedEvent: ",
          "[EventsAdapter#storeDispatchedEvent] Event has already been dispatched", mockEvents.getFirst().id());
    }

    @Test
    void rethrowsOtherErrors() {
      CosmosException error = CosmosException.of("Service unavailable", 503);
      when(cosmos.items.create(any())).thenThrow(error);

      assertThatThrownBy(() -> EventsAdapter.storeDispatchedEvent(cosmos.client, mockEvents.getFirst(), config))
          .isSameAs(error);
      verify(logger).error("[Booster]|events-adapter#storeDispatchedEvent: ",
          "[EventsAdapter#storeDispatchedEvent] Error storing dispatched event", error);
    }
  }

  @Test
  void envelopesRoundTripTheirDocuments() {
    Map<String, Object> document = EventHelper.withSystemProperties(mockEvents.getFirst());
    EventEnvelope envelope = EventsAdapter.rawEventsToEnvelopes(List.of(document)).getFirst();

    assertThat(EventDocuments.documentFor(envelope)).isEqualTo(BoosterJson.toRaw(document));
    assertThat(EventDocuments.documentFor(EventHelper.mockEventEnvelope())).isNull();
    verify(cosmos.database, org.mockito.Mockito.never()).container(anyString());
    assertThat(FeedResponse.of(List.of()).resources()).isEmpty();
  }
}
