package com.boostercloud.framework.provider.azure.contract;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.common.HttpService;
import com.boostercloud.framework.provider.azure.AzureClients;
import com.boostercloud.framework.provider.azure.AzureProviderLibrary;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosConnectionString;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRestClient;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRetryOptions;
import com.boostercloud.framework.provider.azure.eventhubs.EventHubRetryOptions;
import com.boostercloud.framework.provider.azure.eventhubs.RetryMode;
import com.boostercloud.framework.provider.azure.eventhubs.rest.EventHubConnectionString;
import com.boostercloud.framework.provider.azure.eventhubs.rest.EventHubsRestProducerClient;
import com.boostercloud.framework.provider.azure.support.EventHelper;
import com.boostercloud.framework.provider.azure.support.FakeAzureServer;
import com.boostercloud.framework.provider.azure.support.FakeAzureServer.RecordedRequest;
import com.boostercloud.framework.provider.azure.support.FakeAzureServer.Response;
import com.boostercloud.framework.provider.azure.support.Fixtures;
import com.boostercloud.framework.provider.azure.types.AzureCosmosDBFunctionInput;
import com.boostercloud.framework.provider.azure.types.AzureEventHubFunctionInput;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.provider.azure.webpubsub.rest.WebPubSubConnectionString;
import com.boostercloud.framework.provider.azure.webpubsub.rest.WebPubSubRestServiceClient;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.Level;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.envelope.PaginatedEntityIdResult;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import com.boostercloud.framework.types.graphql.GraphQLInitAck;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end contract tests of the provider library: the real adapters and the real REST clients run against the
 * in-JVM fake Azure server, and each test pins the HTTP conversation a Booster operation produces.
 */
class AzureProviderContractTest {

  static final String KEY = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";
  static final Instant NOW = Instant.parse("2026-11-10T09:15:04Z");

  private FakeAzureServer server;
  private BoosterConfig config;
  private AzureProviderLibrary provider;
  private final AtomicInteger ids = new AtomicInteger();

  @BeforeEach
  void start() {
    server = new FakeAzureServer();
    config = new BoosterConfig("production");
    config.logLevel = Level.ERROR;
    HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    CosmosRestClient cosmos = new CosmosRestClient(
        CosmosConnectionString.parse("AccountEndpoint=" + server.baseUrl() + "/;AccountKey=" + KEY + ";"),
        CosmosRetryOptions.defaults(), http, clock, millis -> { }, () -> "generated-" + ids.incrementAndGet());
    EventHubsRestProducerClient producer = new EventHubsRestProducerClient(
        EventHubConnectionString.parse("Endpoint=" + server.baseUrl() + "/;SharedAccessKeyName=send;SharedAccessKey=k;"
            + "EntityPath=booster_events"),
        "booster_events", EventHubRetryOptions.of(5, RetryMode.EXPONENTIAL), http, clock, millis -> { }, () -> 0.5);
    provider = new AzureProviderLibrary(List.of(), AzureClients.of(cosmos, producer,
        (connectionString, hub) -> new WebPubSubRestServiceClient(WebPubSubConnectionString.parse(connectionString), hub,
            http, clock, millis -> { }, () -> 0.0),
        (url, method, data) -> new HttpService.PostResult(200, "")));
    Dates.useClock(Clock.fixed(Instant.parse("2026-11-10T09:15:02.123Z"), ZoneOffset.UTC));
  }

  @AfterEach
  void stop() {
    server.close();
    Dates.resetClock();
    EnvironmentVariables.clearOverrides();
  }

  private static Response container(String id, String partitionKeyPath) {
    return Response.json(200, Fixtures.read("cosmos/container.json")
        .replace("{{containerId}}", id).replace("{{partitionKeyPath}}", partitionKeyPath));
  }

  private static Response documents(String continuation, Object... documents) {
    Response response = Response.json(200, BoosterJson.toJson(obj("_rid", "x", "Documents", List.of(documents),
        "_count", documents.length)));
    return continuation == null ? response : response.withHeader("x-ms-continuation", continuation);
  }

  @Test
  void storingEventsSendsOneTransactionalBatchPerEntity() {
    server.enqueue(Fixtures.response("cosmos/batch-200.json", 200)).enqueue(Fixtures.response("cosmos/batch-200.json", 200));

    List<EventEnvelope> stored = provider.events().store(List.of(
        EventHelper.nonPersisted("Cart", "42"), EventHelper.nonPersisted("Cart", "43"),
        EventHelper.nonPersisted("Cart", "42")), config);

    assertThat(server.requests()).hasSize(2);
    RecordedRequest first = server.request(0);
    assertThat(first.path()).isEqualTo("/dbs/new-booster-app-app/colls/new-booster-app-app-events-store/docs");
    assertThat(first.header("x-ms-cosmos-is-batch-request")).isEqualTo("True");
    assertThat(first.header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Cart-42-event\"]");
    Map<String, Object> resource = obj("version", 1, "kind", "event", "superKind", "domain", "entityID", "42",
        "requestID", "request-1", "entityTypeName", "Cart", "typeName", "CartChanged", "value", obj("entityId", "42"),
        "createdAt", "2026-11-10T09:15:02.123Z", "entityTypeName_entityID_kind", "Cart-42-event");
    Map<String, Object> firstResource = new java.util.LinkedHashMap<>(resource);
    firstResource.put("id", "generated-1");
    Map<String, Object> secondResource = new java.util.LinkedHashMap<>(resource);
    secondResource.put("id", "generated-2");
    assertThat(first.json()).isEqualTo(List.of(obj("operationType", "Create", "resourceBody", firstResource),
        obj("operationType", "Create", "resourceBody", secondResource)));
    assertThat(server.request(1).header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Cart-43-event\"]");
    assertThat(stored).extracting(event -> event.entityID().value()).containsExactly("42", "42", "43");
  }

  @Test
  void aRejectedBatchFailsTheStore() {
    server.enqueue(Fixtures.response("cosmos/batch-207.json", 207).withHeader("x-ms-substatus", "0"));

    assertThatThrownBy(() -> provider.events().store(List.of(EventHelper.nonPersisted("Cart", "42")), config))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Error 0 storing events: {");
  }

  @Test
  void readingTheEventsOfAnEntityIsASinglePartitionQuery() {
    server.enqueue(documents(null, BoosterJson.parse(Fixtures.read("cosmos/event-document.json"))));

    List<EventEnvelope> events = provider.events().forEntitySince(config, "Cart", UUID.of("42"), null);

    assertThat(server.requests()).hasSize(1);
    RecordedRequest query = server.request(0);
    assertThat(query.header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Cart-42-event\"]");
    assertThat(query.header("x-ms-cosmos-is-query-plan-request")).isNull();
    assertThat(query.json()).isEqualTo(obj("query", "SELECT * FROM c WHERE c[\"entityTypeName_entityID_kind\"] = "
        + "@partitionKey AND c[\"createdAt\"] > @fromTime AND NOT IS_DEFINED(c[\"deletedAt\"]) ORDER BY c[\"createdAt\"] ASC",
        "parameters", List.of(obj("name", "@partitionKey", "value", "Cart-42-event"),
            obj("name", "@fromTime", "value", "1970-01-01T00:00:00.000Z"))));
    assertThat(events).singleElement().satisfies(event -> {
      assertThat(event.id()).isEqualTo("3f6e1c2a-8b7d-4e5f-9a0b-1c2d3e4f5a6b");
      assertThat(event.typeName()).isEqualTo("CartItemAdded");
      assertThat(event.createdAt()).isEqualTo("2026-11-10T09:15:02.123Z");
    });
  }

  @Test
  void dispatchedMarkersUseTheUniqueKeyConflictToDetectDuplicates() {
    server.enqueue(container("new-booster-app-app-dispatched-events", "/eventId"))
        .enqueue(Response.json(201, "{\"eventId\":\"e1\",\"id\":\"generated-1\"}"))
        .enqueue(Fixtures.response("cosmos/error-409.json", 409));
    EventEnvelope event = EventHelper.nonPersisted("Cart", "42").persisted("t").toBuilder().id("e1").build();

    assertThat(provider.events().storeDispatched(event, config)).isTrue();
    assertThat(provider.events().storeDispatched(event, config)).isFalse();

    assertThat(server.request(1).json()).isEqualTo(obj("eventId", "e1", "id", "generated-1"));
    assertThat(server.request(1).header("x-ms-documentdb-partitionkey")).isEqualTo("[\"e1\"]");
    assertThat(server.request(2).json()).isEqualTo(obj("eventId", "e1", "id", "generated-2"));
  }

  @Test
  void readModelsAreInsertedUpdatedWithTheirEtagAndConflictsBecomeOptimisticConcurrencyErrors() {
    String container = "new-booster-app-app-CartReadModel";
    server.enqueue(container(container, "/id"))
        .enqueue(Fixtures.response("cosmos/read-model-document.json", 201))
        .enqueue(Fixtures.response("cosmos/read-model-document.json", 200))
        .enqueue(Fixtures.response("cosmos/error-412.json", 412))
        .enqueue(Fixtures.response("cosmos/read-model-document.json", 200));

    provider.readModels().store(config, "CartReadModel", obj("id", "42", "boosterMetadata", obj("version", 1)), null);
    List<Map<String, Object>> fetched = provider.readModels().fetch(config, "CartReadModel", UUID.of("42"), null);
    Map<String, Object> next = new java.util.LinkedHashMap<>(fetched.getFirst());
    next.put("boosterMetadata", obj("version", 4, "optimisticConcurrencyValue",
        ((Map<?, ?>) fetched.getFirst().get("boosterMetadata")).get("optimisticConcurrencyValue")));

    assertThatThrownBy(() -> provider.readModels().store(config, "CartReadModel", next, 3L))
        .isInstanceOf(OptimisticConcurrencyUnexpectedVersionError.class)
        .hasMessageStartingWith("Operation cannot be performed because one of the specified precondition is not met.");
    provider.readModels().store(config, "CartReadModel", next, 3L);

    assertThat(server.request(1).header("x-ms-documentdb-partitionkey")).isEqualTo("[\"42\"]");
    assertThat(server.request(1).header("x-ms-documentdb-is-upsert")).isNull();
    assertThat(server.request(2).method()).isEqualTo("GET");
    assertThat(fetched.getFirst().get("boosterMetadata")).isEqualTo(obj("version", 3, "schemaVersion", 1,
        "lastUpdateAt", "2026-11-10T09:15:03.500Z", "optimisticConcurrencyValue", "\"0800a1b2-0000-0d00-0000-65a000000002\""));
    RecordedRequest update = server.request(3);
    assertThat(update.header("x-ms-documentdb-is-upsert")).isEqualTo("True");
    assertThat(update.header("if-match")).isEqualTo("\"0800a1b2-0000-0d00-0000-65a000000002\"");
    assertThat(server.requests()).hasSize(5);
  }

  @Test
  void paginatedReadModelSearchesUseTheGatewayContinuationTokens() {
    server.enqueue(Fixtures.response("cosmos/query-plan-gateway.json", 200))
        .enqueue(documents("+RID:~abc#RT:1#TRC:2#ISV:2#IEO:65567#QCF:8", obj("id", "1", "_etag", "\"e1\""),
            obj("id", "2", "_etag", "\"e2\"")));

    ReadModelListResult<Map<String, Object>> page = provider.readModels().search(config, "CartReadModel",
        obj("checks", obj("gt", 0)), null, 2, null, true, null);

    RecordedRequest query = server.request(1);
    assertThat(query.path()).isEqualTo("/dbs/new-booster-app-app/colls/new-booster-app-app-CartReadModel/docs");
    assertThat(query.header("x-ms-max-item-count")).isEqualTo("2");
    assertThat(query.json()).isEqualTo(obj("query", "SELECT * FROM c WHERE c[\"checks\"] > @checks_0",
        "parameters", List.of(obj("name", "@checks_0", "value", 0))));
    assertThat(page.count()).isEqualTo(2);
    assertThat(page.cursor()).containsExactly(Map.entry("continuationToken", "+RID:~abc#RT:1#TRC:2#ISV:2#IEO:65567#QCF:8"),
        Map.entry("id", "2"));
    assertThat(page.items().getFirst().get("boosterMetadata")).isEqualTo(obj("optimisticConcurrencyValue", "\"e1\""));
  }

  @Test
  void entityIdsAreDistinctPagesComputedFromEveryRange() {
    server.enqueue(Fixtures.response("cosmos/query-plan-distinct.json", 200))
        .enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
        .enqueue(documents(null, obj("entityID", "1"), obj("entityID", "2")))
        .enqueue(documents(null, obj("entityID", "3")));

    PaginatedEntitiesIdsResult page = provider.events().searchEntitiesIDs(config, 2, Map.of("id", "1"), "Cart");

    assertThat(server.request(0).json()).isEqualTo(obj("query", "SELECT DISTINCT c.entityID FROM c WHERE c[\"kind\"] = "
        + "@kind_0 AND c[\"entityTypeName\"] = @entityTypeName_0 AND NOT IS_DEFINED(c[\"deletedAt\"]) OFFSET 1 LIMIT 2 ",
        "parameters", List.of(obj("name", "@kind_0", "value", "event"), obj("name", "@entityTypeName_0", "value", "Cart"))));
    assertThat(page).isEqualTo(new PaginatedEntitiesIdsResult(
        List.of(new PaginatedEntityIdResult(UUID.of("2")), new PaginatedEntityIdResult(UUID.of("3"))), 2,
        Map.of("id", "3")));
  }

  @Test
  void theStreamCarriesTheChangeFeedDocumentsAndTheConsumerDeduplicatesOnTheirEtag() {
    Object document = BoosterJson.parse(Fixtures.read("cosmos/event-document.json"));
    server.enqueue(Response.empty(201))
        .enqueue(container("new-booster-app-app-events-dedup", "/primaryKey"))
        .enqueue(Response.json(201, "{\"id\":\"generated-1\"}"))
        .enqueue(Fixtures.response("cosmos/error-409.json", 409));

    List<EventEnvelope> envelopes = provider.events().rawToEnvelopes(
        new AzureCosmosDBFunctionInput(List.of(document), AzureInvocationContext.of("i")));
    provider.events().produce("Cart", UUID.of("42"), envelopes, config);
    String message = (String) ((Map<?, ?>) ((List<?>) server.request(0).json()).getFirst()).get("Body");
    AzureEventHubFunctionInput delivery =
        new AzureEventHubFunctionInput(List.of(message, message), AzureInvocationContext.of("j"));
    List<Object> deduplicated = provider.events().dedupEventStream(config, delivery);
    List<EventEnvelope> consumed = provider.events().rawStreamToEnvelopes(config, delivery, deduplicated);

    RecordedRequest send = server.request(0);
    assertThat(send.path()).isEqualTo("/booster_events/messages");
    assertThat(send.json()).isEqualTo(List.of(obj("Body", BoosterJson.toJson(document),
        "BrokerProperties", obj("PartitionKey", "Cart-42-event"))));
    assertThat(server.request(2).header("x-ms-documentdb-partitionkey"))
        .isEqualTo("[\"\\\"0800f1c2-0000-0d00-0000-65a000000000\\\"\"]");
    assertThat(server.request(2).json()).isEqualTo(obj("primaryKey", "\"0800f1c2-0000-0d00-0000-65a000000000\"",
        "createdAt", "2026-11-10T09:15:02.123Z", "ttl", 86400, "id", "generated-1"));
    assertThat(deduplicated).containsExactly(message);
    assertThat(consumed).containsExactlyElementsOf(envelopes);
  }

  @Test
  void anEntityWhoseEventsExceedTheBasicTierLimitIsStreamedInSeveralPublications() {
    String template = Fixtures.read("cosmos/event-document.json");
    List<Object> documents = new java.util.ArrayList<>();
    for (int i = 0; i < 100; i++) {
      @SuppressWarnings("unchecked")
      Map<String, Object> document = (Map<String, Object>) BoosterJson.parse(template);
      document.put("id", "event-" + i);
      document.put("value", obj("blob", "x".repeat(3000)));
      documents.add(document);
    }
    for (int i = 0; i < 3; i++) {
      server.enqueue(Response.empty(201));
    }

    List<EventEnvelope> envelopes = provider.events().rawToEnvelopes(
        new AzureCosmosDBFunctionInput(documents, AzureInvocationContext.of("i")));
    provider.events().produce("Cart", UUID.of("42"), envelopes, config);

    List<RecordedRequest> sends = server.requests();
    assertThat(sends).hasSizeGreaterThan(1).allSatisfy(send -> {
      assertThat(send.path()).isEqualTo("/booster_events/messages");
      assertThat(send.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(256 * 1024);
    });
    assertThat(sends.stream().mapToInt(send -> ((List<?>) send.json()).size()).sum()).isEqualTo(100);
  }

  @Test
  void subscriptionsAndWebsocketMessages() {
    EnvironmentVariables.set("WebPubSubConnectionString", "Endpoint=" + server.baseUrl() + ";AccessKey=key;Version=1.0;");
    server.enqueue(container("new-booster-app-app-subscriptions-store", "/className"))
        .enqueue(Response.json(201, "{}"))
        .enqueue(documents(null, obj("className", "CartReadModel", "connectionID", "c1", "expirationTime", 9,
            "version", 1, "operation", obj("id", "1", "query", "subscription { a }"))))
        .enqueue(Response.empty(202));

    provider.readModels().subscribe(config, SubscriptionEnvelope.builder().className("CartReadModel").version(1)
        .connectionID("c1").expirationTime(9).operation(GraphQLOperation.builder().id("1").query("subscription { a }")
            .build()).build());
    List<SubscriptionEnvelope> subscriptions = provider.readModels().fetchSubscriptions(config, "CartReadModel");
    provider.connections().sendMessage(config, "c1", new GraphQLInitAck());

    assertThat(server.request(1).header("x-ms-documentdb-partitionkey")).isEqualTo("[\"CartReadModel\"]");
    assertThat(server.request(1).json()).isEqualTo(obj("className", "CartReadModel", "version", 1,
        "expirationTime", 9, "connectionID", "c1", "operation", obj("query", "subscription { a }", "id", "1"),
        "connectionID_subscriptionID", "c1-1", "subscriptionID", "1", "ttl", 9, "id", "generated-1"));
    assertThat(server.request(2).header("x-ms-documentdb-partitionkey")).isEqualTo("[\"CartReadModel\"]");
    assertThat(subscriptions).singleElement().extracting(SubscriptionEnvelope::connectionID).isEqualTo("c1");
    RecordedRequest message = server.request(3);
    assertThat(message.path()).isEqualTo("/api/hubs/booster/connections/c1/:send");
    assertThat(message.header("content-type")).isEqualTo("text/plain");
    assertThat(message.body()).isEqualTo("{\"type\":\"connection_ack\"}");
  }

  @Test
  void healthChecksRunAggregateQueriesOnEveryRange() {
    server.enqueue(Fixtures.response("cosmos/query-plan-top.json", 200))
        .enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
        .enqueue(documents(null, 1)).enqueue(documents(null))
        .enqueue(Fixtures.response("cosmos/query-plan-count.json", 200))
        .enqueue(Fixtures.response("cosmos/pkranges-two.json", 200))
        .enqueue(documents(null, List.of(obj("item", 5)))).enqueue(documents(null, List.of(obj("item", 6))));

    assertThat(provider.sensor().isDatabaseEventUp(config)).isTrue();
    assertThat(provider.sensor().databaseEventsHealthDetails(config))
        .isEqualTo(obj("url", "dbs/new-booster-app-app/colls/new-booster-app-app-events-store", "count", 11));
  }
}
