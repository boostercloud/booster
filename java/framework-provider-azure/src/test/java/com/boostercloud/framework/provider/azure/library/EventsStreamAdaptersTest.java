package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.azure.cosmos.CosmosException;
import com.boostercloud.framework.provider.azure.cosmos.ItemResponse;
import com.boostercloud.framework.provider.azure.eventhubs.CreateBatchOptions;
import com.boostercloud.framework.provider.azure.eventhubs.EventData;
import com.boostercloud.framework.provider.azure.eventhubs.EventDataBatch;
import com.boostercloud.framework.provider.azure.eventhubs.EventHubProducerClient;
import com.boostercloud.framework.provider.azure.support.CosmosMocks;
import com.boostercloud.framework.provider.azure.support.EventHelper;
import com.boostercloud.framework.provider.azure.types.AzureEventHubFunctionInput;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.EventStreamConfiguration;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tests of the stream producer and consumer adapters (no TypeScript tests exist). */
class EventsStreamAdaptersTest {

  private BoosterConfig config;
  private Logger logger;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    logger = mock(Logger.class);
    config.logger = logger;
  }

  @AfterEach
  void clearEnvironment() {
    EnvironmentVariables.clearOverrides();
  }

  /** A batch that accepts {@code capacity} events. */
  private static final class FakeBatch implements EventDataBatch {
    private final int capacity;
    private final List<EventData> events = new ArrayList<>();

    FakeBatch(int capacity) {
      this.capacity = capacity;
    }

    @Override
    public boolean tryAdd(EventData eventData) {
      if (events.size() >= capacity) {
        return false;
      }
      events.add(eventData);
      return true;
    }

    @Override
    public int count() {
      return events.size();
    }

    @Override
    public long sizeInBytes() {
      return events.size();
    }

    @Override
    public long maxSizeInBytes() {
      return capacity;
    }

    @Override
    public String partitionKey() {
      return null;
    }

    @Override
    public List<EventData> events() {
      return events;
    }
  }

  @Nested
  class TheProducer {

    private final EventHubProducerClient producer = mock(EventHubProducerClient.class);
    private final List<FakeBatch> sent = new ArrayList<>();

    private void batchesOf(int capacity) {
      when(producer.createBatch(any())).thenAnswer(ignored -> new FakeBatch(capacity));
      org.mockito.Mockito.doAnswer(invocation -> sent.add(invocation.getArgument(0))).when(producer).sendBatch(any());
    }

    @Test
    void sendsTheChangeFeedDocumentsWithTheEntityPartitionKeyInGreedyBatches() {
      batchesOf(2);
      List<Object> documents = List.of(
          EventHelper.withSystemProperties(EventHelper.nonPersisted("Cart", "42").persisted("t1")),
          EventHelper.withSystemProperties(EventHelper.nonPersisted("Cart", "42").persisted("t2")),
          EventHelper.withSystemProperties(EventHelper.nonPersisted("Cart", "42").persisted("t3")));
      List<EventEnvelope> envelopes = EventsAdapter.rawEventsToEnvelopes(documents);

      EventsStreamProducerAdapter.produceEventsStream(producer, "Cart", "42", envelopes, config);

      verify(producer, times(2)).createBatch(CreateBatchOptions.withPartitionKey("Cart-42-event"));
      assertThat(sent).extracting(FakeBatch::count).containsExactly(2, 1);
      assertThat(sent.getFirst().events().getFirst().body()).isEqualTo(BoosterJson.toRaw(documents.getFirst()));
      verify(logger).info("[Booster]|events-stream-producer#produceEventsStream: ", "Sent 3 events");
    }

    @Test
    void envelopesItDidNotReadAreSentAsJson() {
      batchesOf(5);
      EventEnvelope envelope = EventHelper.nonPersisted("Cart", "42").persisted("t1").toBuilder()
          .id(java.util.UUID.randomUUID().toString()).build();

      EventsStreamProducerAdapter.produceEventsStream(producer, "Cart", "42", List.of(envelope), config);

      assertThat(sent.getFirst().events().getFirst().body()).isEqualTo(BoosterJson.toRawMap(envelope));
    }

    @Test
    void aSingleEventTooLargeForAnEmptyBatchFails() {
      batchesOf(0);
      EventEnvelope envelope = EventHelper.nonPersisted("Cart", "42").persisted("t1").toBuilder()
          .id(java.util.UUID.randomUUID().toString()).build();

      assertThatThrownBy(() -> EventsStreamProducerAdapter.produceEventsStream(producer, "Cart", "42", List.of(envelope),
          config))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Message was too large and can't be sent until it's made smaller. "
              + BoosterJson.toJson(BoosterJson.toRawMap(envelope)));
    }

    @Test
    void anEmptyInputSendsNothing() {
      batchesOf(1);

      EventsStreamProducerAdapter.produceEventsStream(producer, "Cart", "42", List.of(), config);

      verify(producer).createBatch(any());
      assertThat(sent).isEmpty();
      verify(logger).info("[Booster]|events-stream-producer#produceEventsStream: ", "Sent 0 events");
    }
  }

  @Nested
  class TheConsumer {

    private final CosmosMocks cosmos = new CosmosMocks();

    @Test
    void storesADedupRecordPerMessageAndKeepsTheNewOnes() {
      config.eventStreamConfiguration = EventStreamConfiguration.enabled(
          new EventStreamConfiguration.Parameters("booster_events", 1, 1, 3600, null, null));
      when(cosmos.items.create(any()))
          .thenReturn(ItemResponse.of(201, obj("id", "x")))
          .thenThrow(CosmosException.of("Unique index constraint violation", 409));
      String first = "{\"kind\":\"event\",\"_etag\":\"\\\"e1\\\"\",\"entityTypeName\":\"Cart\",\"entityID\":\"1\"}";
      Map<String, Object> second = obj("kind", "event", "_etag", "\"e2\"", "entityTypeName", "Cart", "entityID", "2");

      List<Object> deduplicated = EventsStreamConsumerAdapter.dedupEventStream(cosmos.client, config,
          new AzureEventHubFunctionInput(List.of(first, second), AzureInvocationContext.of("i")));

      verify(cosmos.database, times(2)).container(config.resourceNames().eventsDedup());
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> records = ArgumentCaptor.forClass(Map.class);
      verify(cosmos.items, times(2)).create(records.capture());
      assertThat(records.getAllValues().getFirst()).containsOnlyKeys("primaryKey", "createdAt", "ttl")
          .containsEntry("primaryKey", "\"e1\"").containsEntry("ttl", 3600);
      assertThat(deduplicated).containsExactly(first);
      verify(logger).warn("[Booster]|dedup-events-stream#dedupEventsStream: ",
          "Ignoring duplicated event with etag \"e2\".");
    }

    @Test
    void theDefaultTtlIsOneDayAndMessagesWithoutEtagUseTheirId() {
      when(cosmos.items.create(any())).thenReturn(ItemResponse.of(201, obj()));

      EventsStreamConsumerAdapter.dedupEventStream(cosmos.client, config, List.of(obj("id", "doc-1")));

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, Object>> record = ArgumentCaptor.forClass(Map.class);
      verify(cosmos.items).create(record.capture());
      assertThat(record.getValue()).containsEntry("primaryKey", "doc-1").containsEntry("ttl", 86400);
    }

    @Test
    void otherErrorsPropagateAndUnknownInputsHaveNoMessages() {
      CosmosException error = CosmosException.of("boom", 500);
      when(cosmos.items.create(any())).thenThrow(error);

      assertThatThrownBy(() -> EventsStreamConsumerAdapter.dedupEventStream(cosmos.client, config,
          List.of(obj("_etag", "e")))).isSameAs(error);
      assertThat(EventsStreamConsumerAdapter.dedupEventStream(cosmos.client, config, "nothing")).isEmpty();
    }

    @Test
    void mapsTheDeduplicatedMessagesToEventEnvelopes() {
      EventEnvelope event = EventHelper.nonPersisted("Cart", "42").persisted("t1").toBuilder().id("d1").build();
      String message = BoosterJson.toJson(EventHelper.withSystemProperties(event));

      List<EventEnvelope> envelopes = EventsStreamConsumerAdapter.rawEventsStreamToEnvelopes(config, null,
          List.of(message, obj("kind", "snapshot")));

      assertThat(envelopes).containsExactly(event);
    }

    @Test
    void logsTheConsumedEventLineWhenBindingDataIsPresent() {
      EnvironmentVariables.set("WEBSITE_INSTANCE_ID", "instance-1");
      EventHubConsumerInput input = new EventHubConsumerInput(List.of(), AzureInvocationContext.of("i"),
          new EventHubConsumerInput.BindingData(List.of("Cart-42-event"), List.of("10"), List.of(7L),
              List.of("2026-11-10T09:15:04Z")));

      EventsStreamConsumerAdapter.rawEventsStreamToEnvelopes(config, input, List.of(obj("kind", "event")));

      verify(logger).debug("[Booster]|events-adapter#rawEventsStreamToEnvelopes: ",
          "CONSUMED_EVENT:instance-1#Cart-42-event=>(0#10#7#2026-11-10T09:15:04Z)");
    }

    @Test
    void theBindingDataGuardIsStructural() {
      EnvironmentVariables.set("WEBSITE_INSTANCE_ID", "instance-2");
      Map<String, Object> input = new LinkedHashMap<>();
      input.put("messages", List.of());
      input.put("context", Map.of("invocationId", "i"));
      input.put("bindingData", obj("partitionKeyArray", List.of("Cart-1-event", "Cart-2-event"),
          "offsetArray", List.of("1", "2"), "sequenceNumberArray", List.of(3, 4),
          "enqueuedTimeUtcArray", List.of("t1", "t2")));

      EventsStreamConsumerAdapter.rawEventsStreamToEnvelopes(config, input,
          List.of(obj("kind", "event"), obj("kind", "event")));

      verify(logger).debug("[Booster]|events-adapter#rawEventsStreamToEnvelopes: ",
          "CONSUMED_EVENT:instance-2#Cart-2-event=>(1#2#4#t2)");
      assertThat(EventsStreamConsumerAdapter.isEventHubConsumerInput(input)).isTrue();
      input.put("bindingData", obj("partitionKeyArray", "not an array"));
      assertThat(EventsStreamConsumerAdapter.isEventHubConsumerInput(input)).isFalse();
      input.put("bindingData", "not an object");
      assertThat(EventsStreamConsumerAdapter.isEventHubConsumerInput(input)).isFalse();
      assertThat(EventsStreamConsumerAdapter.isEventHubConsumerInput(List.of())).isFalse();
    }
  }
}
