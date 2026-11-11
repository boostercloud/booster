package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.azure.cosmos.BatchResponse;
import com.boostercloud.framework.provider.azure.cosmos.CosmosException;
import com.boostercloud.framework.provider.azure.cosmos.OperationInput;
import com.boostercloud.framework.provider.azure.cosmos.RequestOptions;
import com.boostercloud.framework.provider.azure.support.CosmosMocks;
import com.boostercloud.framework.provider.azure.support.EventHelper;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.provider.AzureConfiguration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Port of {@code test/library/events-store-adapter.test.ts}, plus grouping, chunking and failure handling. */
class EventsStoreAdapterTest {

  private BoosterConfig config;
  private CosmosMocks cosmos;
  private NonPersistedEventEnvelope mockEvent;
  private Logger logger;

  @BeforeEach
  void setUp() {
    cosmos = new CosmosMocks();
    config = new BoosterConfig("test");
    logger = mock(Logger.class);
    config.logger = logger;
    mockEvent = EventHelper.mockEventEnvelope().toNonPersisted();
  }

  private Map<String, Object> expectedDocument(NonPersistedEventEnvelope event, Object createdAt) {
    Map<String, Object> document = new LinkedHashMap<>(BoosterJson.toRawMap(event.persisted((String) createdAt)));
    document.put("entityTypeName_entityID_kind",
        PartitionKeys.partitionKeyForEvent(event.entityTypeName(), event.entityID()));
    document.put("createdAt", createdAt);
    return document;
  }

  @SuppressWarnings("unchecked")
  private List<List<OperationInput>> batches(int times) {
    ArgumentCaptor<List<OperationInput>> operations = ArgumentCaptor.forClass(List.class);
    verify(cosmos.items, times(times)).batch(operations.capture(), anyString(), any(RequestOptions.class));
    return operations.getAllValues();
  }

  @Nested
  class TheStoreEventsMethod {

    @Test
    void publishesTheEventEnvelopesPassedViaParameterInBatches() {
      List<EventEnvelope> stored = EventsStoreAdapter.storeEvents(cosmos.client, List.of(mockEvent), config);

      verify(cosmos.client).database(config.resourceNames().applicationStack());
      verify(cosmos.database).container(config.resourceNames().eventsStore());
      List<OperationInput> operations = batches(1).getFirst();
      assertThat(operations).hasSize(1);
      assertThat(operations.getFirst().operationType()).isEqualTo("Create");
      Object createdAt = operations.getFirst().resourceBody().get("createdAt");
      assertThat(createdAt).isNotNull();
      assertThat(operations.getFirst().resourceBody()).isEqualTo(expectedDocument(mockEvent, createdAt));
      verify(cosmos.items).batch(any(),
          eq(PartitionKeys.partitionKeyForEvent(mockEvent.entityTypeName(), mockEvent.entityID())),
          eq(RequestOptions.none()));
      assertThat(stored).containsExactly(mockEvent.persisted((String) createdAt));
      assertThat(stored.getFirst().id()).isNull();
    }

    @Test
    void publishesTheEventEnvelopesOneByOneIfBatchingIsDisabled() {
      config.azureConfiguration.enableEventBatching = false;

      EventsStoreAdapter.storeEvents(cosmos.client, List.of(mockEvent), config);

      verify(cosmos.client).database(config.resourceNames().applicationStack());
      verify(cosmos.database).container(config.resourceNames().eventsStore());
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> document = ArgumentCaptor.forClass(Map.class);
      verify(cosmos.items).create(document.capture());
      Object createdAt = document.getValue().get("createdAt");
      assertThat(createdAt).isNotNull();
      assertThat(document.getValue()).isEqualTo(expectedDocument(mockEvent, createdAt));
      verify(cosmos.items, never()).batch(any(), any(), any());
    }

    @Test
    void groupsByPartitionKeyAndReturnsTheEnvelopesGroupedByPartition() {
      NonPersistedEventEnvelope a1 = EventHelper.nonPersisted("Cart", "a");
      NonPersistedEventEnvelope b1 = EventHelper.nonPersisted("Cart", "b");
      NonPersistedEventEnvelope a2 = a1.toBuilder().typeName("Second").build();

      List<EventEnvelope> stored = EventsStoreAdapter.storeEvents(cosmos.client, List.of(a1, b1, a2), config);

      assertThat(stored).extracting(EventEnvelope::entityID, EventEnvelope::typeName).containsExactly(
          org.assertj.core.groups.Tuple.tuple(a1.entityID(), "CartChanged"),
          org.assertj.core.groups.Tuple.tuple(a2.entityID(), "Second"),
          org.assertj.core.groups.Tuple.tuple(b1.entityID(), "CartChanged"));
      List<List<OperationInput>> batches = batches(2);
      assertThat(batches.get(0)).hasSize(2);
      assertThat(batches.get(1)).hasSize(1);
      verify(cosmos.items).batch(any(), eq("Cart-a-event"), any());
      verify(cosmos.items).batch(any(), eq("Cart-b-event"), any());
    }

    @Test
    void splitsEachPartitionInChunksOfTheConfiguredBatchSize() {
      config.azureConfiguration.cosmos.batchSize = 2;
      NonPersistedEventEnvelope event = EventHelper.nonPersisted("Cart", "a");

      EventsStoreAdapter.storeEvents(cosmos.client, List.of(event, event, event, event, event), config);

      assertThat(batches(3)).extracting(List::size).containsExactly(2, 2, 1);
    }

    @Test
    void passesTheConfiguredRequestOptions() {
      config.azureConfiguration.cosmos.requestOptions = new AzureConfiguration.RequestOptions("Strong", null, "Exclude");

      EventsStoreAdapter.storeEvents(cosmos.client, List.of(mockEvent), config);

      verify(cosmos.items).batch(any(), anyString(), eq(new RequestOptions(null, "Strong", null, "Exclude")));
    }

    @Test
    void aFailedBatchThrowsWithItsSubstatusAndResponse() {
      BatchResponse failed = new BatchResponse(Map.of(), List.of(new BatchResponse.OperationResponse(409, 1.2, null,
          null)), 207, 1001);
      when(cosmos.items.batch(any(), any(), any())).thenReturn(failed);

      String json = BoosterJson.toJson(failed);
      assertThatThrownBy(() -> EventsStoreAdapter.storeEvents(cosmos.client, List.of(mockEvent), config))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Error 1001 storing events: " + json);
      verify(logger).error("[Booster]|store-events-adapter#storeEventsInBatch: ",
          "An error ocurred storing a batch of events. Batch response: " + json);
    }

    @Test
    void anUnexpectedErrorIsLoggedAndRethrown() {
      CosmosException error = CosmosException.of("Request size is too large", 413);
      when(cosmos.items.batch(any(), any(), any())).thenThrow(error);

      assertThatThrownBy(() -> EventsStoreAdapter.storeEvents(cosmos.client, List.of(mockEvent), config)).isSameAs(error);
      verify(logger).error("[Booster]|store-events-adapter#batchEvents: ", "Unexpected error storing events", error);
    }

    @Test
    void chunkingFollowsArrayFromSemantics() {
      assertThat(EventsStoreAdapter.chunkEvents(List.of(1, 2, 3), 2)).containsExactly(List.of(1, 2), List.of(3));
      assertThat(EventsStoreAdapter.chunkEvents(List.of(), 0)).isEmpty();
      assertThat(EventsStoreAdapter.chunkEvents(List.of(1), -1)).isEmpty();
      assertThatThrownBy(() -> EventsStoreAdapter.chunkEvents(List.of(1), 0))
          .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid array length");
    }

    @Test
    void documentsKeepEveryEnvelopeField() {
      Map<String, Object> document = EventsStoreAdapter.storableResource(config,
          EventHelper.nonPersisted("Cart", "42").persisted("2026-11-10T09:15:02.123Z"));

      assertThat(document).containsExactly(
          Map.entry("version", 1), Map.entry("kind", "event"), Map.entry("superKind", "domain"),
          Map.entry("entityID", "42"), Map.entry("requestID", "request-1"), Map.entry("entityTypeName", "Cart"),
          Map.entry("typeName", "CartChanged"), Map.entry("value", obj("entityId", "42")),
          Map.entry("createdAt", "2026-11-10T09:15:02.123Z"), Map.entry("entityTypeName_entityID_kind", "Cart-42-event"));
    }
  }
}
