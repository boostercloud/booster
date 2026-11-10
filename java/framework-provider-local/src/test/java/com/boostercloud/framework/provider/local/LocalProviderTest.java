package com.boostercloud.framework.provider.local;

import static com.boostercloud.framework.provider.local.Raw.list;
import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.local.library.ApiAdapter;
import com.boostercloud.framework.provider.local.library.LocalHttpRequest;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.EventParametersFilterByEntity;
import com.boostercloud.framework.types.envelope.EventParametersFilterByType;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.EventType;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelope;
import com.boostercloud.framework.types.envelope.HealthEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.envelope.ReadModelEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/** The provider SPI end to end over real data files, with a fake application standing in for core. */
class LocalProviderTest {

  @TempDir
  Path project;

  private LocalProviderRuntime runtime;
  private LocalProvider provider;
  private FakeUserApp app;
  private BoosterConfig config;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = Mockito.mock(Logger.class);
    app = new FakeUserApp(config);
    runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(project).withWebSocketServer(false, "0").withUserApp(() -> app));
    provider = new LocalProvider(runtime, List.of(new RocketDescriptor("com.example.rocket", null)),
        Provider::loadInfrastructurePackage);
    app.provider(provider);
  }

  @AfterEach
  void tearDown() {
    runtime.close();
    Dates.resetClock();
  }

  private static NonPersistedEventEnvelope event(String entityType, String entityId, String type, Object value) {
    return NonPersistedEventEnvelope.builder()
        .version(1).superKind(SuperKindType.DOMAIN).entityID(UUID.of(entityId)).requestID(UUID.generate())
        .entityTypeName(entityType).typeName(type).value(value).build();
  }

  private void useClock(String instant) {
    Dates.useClock(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
  }

  @Test
  void storesEventsAndDispatchesThemInProcess() {
    List<EventEnvelope> stored = provider.events().store(List.of(
        event("Cart", "c1", "ItemAdded", map("sku", "a")), event("Cart", "c1", "ItemAdded", map("sku", "b"))), config);
    assertThat(stored).allSatisfy(e -> {
      assertThat(e.id()).matches("[0-9A-Za-z]{16}");
      assertThat(e.createdAt()).isNotNull();
    });
    assertThat(app.dispatchedBatches).containsExactly(stored);
    assertThat(app.processedEvents).containsExactlyElementsOf(stored);
    List<EventEnvelope> read = provider.events().forEntitySince(config, "Cart", UUID.of("c1"));
    // events of the same millisecond keep their insertion order
    assertThat(read).extracting(e -> (Object) ((Map<?, ?>) e.value()).get("sku")).containsExactly("a", "b");
    assertThat(read).extracting(EventEnvelope::id).containsExactlyElementsOf(stored.stream().map(EventEnvelope::id).toList());
    assertThat(runtime.dispatchJournal().size()).isZero();
    assertThat(Files.exists(project.resolve(".booster/events.json"))).isTrue();
  }

  @Test
  void eventHandlersCanStoreEventsReentrantly() {
    app.onEvent(event -> {
      if (event.typeName().equals("First")) {
        provider.events().store(List.of(event("Cart", "c1", "Second", map())), config);
      }
    });
    provider.events().store(List.of(event("Cart", "c1", "First", map())), config);
    assertThat(app.processedEvents).extracting(EventEnvelope::typeName).containsExactly("First", "Second");
    assertThat(runtime.dispatchJournal().size()).isZero();
  }

  @Test
  void emptyBatchesAreDispatchedToo() {
    assertThat(provider.events().store(List.of(), config)).isEmpty();
    assertThat(app.dispatchedBatches).containsExactly(List.of());
  }

  @Test
  void snapshotsAndEventsSince() {
    useClock("2026-01-01T00:00:00.000Z");
    provider.events().store(List.of(event("Cart", "c1", "A", map())), config);
    useClock("2026-01-01T00:00:01.000Z");
    provider.events().store(List.of(event("Cart", "c1", "B", map())), config);
    NonPersistedEntitySnapshotEnvelope snapshot = NonPersistedEntitySnapshotEnvelope.builder()
        .version(1).superKind(SuperKindType.DOMAIN).requestID(UUID.generate()).entityID(UUID.of("c1"))
        .entityTypeName("Cart").typeName("Cart").value(map("id", "c1", "n", 1))
        .snapshottedEventCreatedAt("2026-01-01T00:00:00.000Z").build();
    useClock("2026-01-01T00:00:02.000Z");
    EntitySnapshotEnvelope persisted = provider.events().storeSnapshot(snapshot, config);
    assertThat(persisted.createdAt()).isEqualTo("2026-01-01T00:00:00.000Z");
    assertThat(persisted.persistedAt()).isEqualTo("2026-01-01T00:00:02.000Z");
    provider.events().storeSnapshot(snapshot.toBuilder().snapshottedEventCreatedAt("2025-12-31T00:00:00.000Z")
        .value(map("id", "c1", "n", 0)).build(), config);
    EntitySnapshotEnvelope latest = provider.events().latestEntitySnapshot(config, "Cart", UUID.of("c1")).orElseThrow();
    assertThat(latest.value()).isEqualTo(map("id", "c1", "n", 1));
    assertThat(latest.id()).isNotNull();
    assertThat(provider.events().forEntitySince(config, "Cart", UUID.of("c1"), latest.snapshottedEventCreatedAt()))
        .extracting(EventEnvelope::typeName).containsExactly("B");
    assertThat(provider.events().forEntitySince(config, "Cart", UUID.of("c1"), "")).hasSize(2);
    assertThat(provider.events().latestEntitySnapshot(config, "Cart", UUID.of("nope"))).isEmpty();
  }

  @Test
  void searchAndPaginatedEntityIds() {
    String[] times = {"2026-01-01T00:00:00.000Z", "2026-01-01T00:00:01.000Z", "2026-01-01T00:00:02.000Z",
        "2026-01-01T00:00:03.000Z"};
    String[] entities = {"c1", "c2", "c1", "c3"};
    for (int i = 0; i < times.length; i++) {
      useClock(times[i]);
      provider.events().store(List.of(event("Cart", entities[i], i % 2 == 0 ? "Even" : "Odd", map("i", i))), config);
    }
    List<EventSearchResponse> byEntity = provider.events().search(config,
        new EventParametersFilterByEntity("Cart", "c1", null, null, null));
    assertThat(byEntity).extracting(EventSearchResponse::createdAt).containsExactly(times[2], times[0]);
    assertThat(byEntity.get(0).type()).isEqualTo("Even");
    assertThat(byEntity.get(0).entity()).isEqualTo("Cart");
    assertThat(provider.events().search(config, new EventParametersFilterByType("Odd", null, null, null)))
        .extracting(EventSearchResponse::entityID).containsExactly(UUID.of("c3"), UUID.of("c2"));
    assertThat(provider.events().search(config, new EventParametersFilterByEntity("Cart", null, times[1], times[2], 1)))
        .extracting(EventSearchResponse::createdAt).containsExactly(times[2]);

    PaginatedEntitiesIdsResult first = provider.events().searchEntitiesIDs(config, 2, null, "Cart");
    assertThat(first.items()).extracting(item -> item.entityID().value()).containsExactly("c3", "c1");
    assertThat(first.count()).isEqualTo(2);
    assertThat(first.cursor()).isEqualTo(Map.of("id", "2"));
    PaginatedEntitiesIdsResult second = provider.events().searchEntitiesIDs(config, 2, first.cursor(), "Cart");
    assertThat(second.items()).extracting(item -> item.entityID().value()).containsExactly("c2");
    assertThat(second.cursor()).isEqualTo(Map.of("id", "4"));
    PaginatedEntitiesIdsResult nan = provider.events().searchEntitiesIDs(config, 2, Map.of("id", "x"), "Cart");
    assertThat(nan.items()).isEmpty();
    assertThat(nan.cursor()).isEqualTo(Map.of("id", "NaN"));
  }

  @Test
  void deletingEventsAndSnapshots() {
    useClock("2026-01-01T00:00:00.000Z");
    provider.events().store(List.of(event("Cart", "c1", "A", map("secret", 1))), config);
    NonPersistedEntitySnapshotEnvelope snapshot = NonPersistedEntitySnapshotEnvelope.builder()
        .version(1).superKind(SuperKindType.DOMAIN).requestID(UUID.generate()).entityID(UUID.of("c1"))
        .entityTypeName("Cart").typeName("Cart").value(map("id", "c1"))
        .snapshottedEventCreatedAt("2026-01-01T00:00:00.000Z").build();
    provider.events().storeSnapshot(snapshot, config);
    EventDeleteParameters parameters = new EventDeleteParameters("Cart", UUID.of("c1"), "2026-01-01T00:00:00.000Z");
    List<EventEnvelope> deletable = provider.events().findDeletableEvent(config, parameters);
    assertThat(deletable).hasSize(1);
    List<EntitySnapshotEnvelope> snapshots = provider.events().findDeletableSnapshot(config,
        parameters.toSnapshotDeleteParameters());
    assertThat(snapshots).hasSize(1);
    useClock("2026-02-01T00:00:00.000Z");
    provider.events().deleteEvent(config, deletable);
    provider.events().deleteSnapshot(config, snapshots);
    assertThat(provider.events().forEntitySince(config, "Cart", UUID.of("c1"))).isEmpty();
    assertThat(provider.events().latestEntitySnapshot(config, "Cart", UUID.of("c1"))).isEmpty();
    List<EventSearchResponse> search = provider.events().search(config,
        new EventParametersFilterByEntity("Cart", null, null, null, null));
    assertThat(search).singleElement().satisfies(response -> {
      assertThat(response.value()).isEqualTo(map());
      assertThat(response.deletedAt()).isEqualTo("2026-02-01T00:00:00.000Z");
    });
    assertThat(provider.events().findDeletableEvent(config, parameters)).isEmpty();
    provider.events().deleteEvent(config, List.of());
    Mockito.verify(config.logger).warn("[Booster]|events-delete-adapter#deleteEvent: ", "Could not find events to delete");
  }

  @Test
  void readModelsWithOptimisticConcurrency() {
    Map<String, Object> v1 = map("id", "rm1", "n", 1, "boosterMetadata", map("version", 1, "schemaVersion", 1));
    provider.readModels().store(config, "CartReadModel", v1, null);
    assertThatThrownBy(() -> provider.readModels().store(config, "CartReadModel", v1, null))
        .isInstanceOf(OptimisticConcurrencyUnexpectedVersionError.class)
        .hasMessage("Can't insert key \"CartReadModel_rm1_1\", it violates the unique constraint");
    Map<String, Object> v2 = map("id", "rm1", "n", 2, "boosterMetadata", map("version", 2, "schemaVersion", 1));
    assertThatThrownBy(() -> provider.readModels().store(config, "CartReadModel", v2, 5L))
        .isInstanceOf(OptimisticConcurrencyUnexpectedVersionError.class);
    provider.readModels().store(config, "CartReadModel", v2, 1L);
    assertThat(provider.readModels().fetch(config, "CartReadModel", UUID.of("rm1"), null))
        .containsExactly(v2);
    assertThat(provider.readModels().fetch(config, "CartReadModel", UUID.of("missing"), null)).isEmpty();
    assertThat(provider.readModels().fetch(config, "OtherReadModel", UUID.of("rm1"), null)).isEmpty();
    assertThat(app.notifications).hasSize(2);
    assertThat(app.notifications.get(1)).isEqualTo(List.of(new ReadModelEnvelope("CartReadModel", v2)));

    provider.readModels().delete(config, "CartReadModel", v2);
    provider.readModels().delete(config, "CartReadModel", null);
    assertThat(provider.readModels().fetch(config, "CartReadModel", UUID.of("rm1"), null)).isEmpty();
  }

  @Test
  void notificationFailuresAreSwallowedAndDisabledSubscriptionsAreNotNotified() {
    app.onNotification(request -> {
      throw new IllegalStateException("boom");
    });
    Map<String, Object> readModel = map("id", "rm1", "boosterMetadata", map("version", 1, "schemaVersion", 1));
    provider.readModels().store(config, "CartReadModel", readModel, null);
    Mockito.verify(config.logger).error("[Booster]|read-model-adapter#storeReadModel: ", "Error notifying subscription",
        readModel);
    config.enableSubscriptions = false;
    provider.readModels().store(config, "OtherReadModel", readModel, null);
    assertThat(app.notifications).hasSize(1);
  }

  @Test
  void searchingReadModels() {
    for (int i = 0; i < 5; i++) {
      provider.readModels().store(config, "ProductReadModel", map("id", "p" + i, "price", i * 10, "tags",
          i % 2 == 0 ? list("even") : list("odd"), "boosterMetadata", map("version", 1, "schemaVersion", 1)), null);
    }
    provider.readModels().store(config, "OtherReadModel", map("id", "o", "price", 5,
        "boosterMetadata", map("version", 1, "schemaVersion", 1)), null);
    ReadModelListResult<Map<String, Object>> all = provider.readModels().search(config, "ProductReadModel", map(),
        map("price", "DESC"), null, null, false, null);
    assertThat(all.items()).extracting(rm -> rm.get("id")).containsExactly("p4", "p3", "p2", "p1", "p0");
    assertThat(all.cursor()).isNull();
    ReadModelListResult<Map<String, Object>> page = provider.readModels().search(config, "ProductReadModel",
        map("price", map("gte", 10)), map("price", "ASC"), 2, map("id", "1"), true, List.of("id"));
    assertThat(page.items()).containsExactly(map("id", "p2"), map("id", "p3"));
    assertThat(page.count()).isEqualTo(2);
    assertThat(page.cursor()).isEqualTo(Map.of("id", "3"));
    assertThat(provider.readModels().search(config, "ProductReadModel",
        map("or", list(map("tags", map("includes", "odd")), map("id", map("eq", "p0")))), map("price", "ASC"), null,
        null, false, null).items()).extracting(rm -> rm.get("id")).containsExactly("p0", "p1", "p3");
    assertThat(provider.readModels().search(config, "ProductReadModel", map("id", map("beginsWith", "p")),
        map("price", "asc"), 1, null, true, null).cursor()).isEqualTo(Map.of("id", "1"));
  }

  @Test
  void subscriptionsAndConnections() {
    ReadModelRequestEnvelope request = ReadModelRequestEnvelope.builder().requestID(UUID.generate())
        .className("CartReadModel").version(1).filters(map("id", map("eq", "c1"))).build();
    provider.readModels().subscribe(config, SubscriptionEnvelope.of(request, 100, "conn-1",
        GraphQLOperation.builder().id("op-1").query("subscription { x }").build()));
    provider.readModels().subscribe(config, SubscriptionEnvelope.of(request, 100, "conn-1",
        GraphQLOperation.builder().id("op-2").query("subscription { y }").build()));
    provider.readModels().subscribe(config, SubscriptionEnvelope.of(request, 100, "conn-2",
        GraphQLOperation.builder().id("op-1").query("subscription { x }").build()));
    assertThat(provider.readModels().fetchSubscriptions(config, "CartReadModel")).hasSize(3);
    provider.readModels().deleteSubscription(config, "conn-1", "op-2");
    assertThat(provider.readModels().fetchSubscriptions(config, "CartReadModel"))
        .extracting(s -> s.connectionID() + "/" + s.operation().id()).containsExactly("conn-1/op-1", "conn-2/op-1");
    provider.readModels().deleteAllSubscriptions(config, "conn-1");
    assertThat(provider.readModels().fetchSubscriptions(config, "CartReadModel")).hasSize(1);

    ConnectionDataEnvelope data = new ConnectionDataEnvelope(123,
        com.boostercloud.framework.types.envelope.UserEnvelope.builder().username("ada").roles(List.of("User")).build());
    provider.connections().storeData(config, "conn-1", data);
    assertThat(provider.connections().fetchData(config, "conn-1")).contains(data);
    assertThat(provider.connections().fetchData(config, "conn-9")).isEmpty();
    provider.connections().deleteData(config, "conn-1");
    assertThat(provider.connections().fetchData(config, "conn-1")).isEmpty();
    provider.connections().deleteData(config, "conn-1");
    Mockito.verify(config.logger).info("[Booster]|connections-adapter#deleteConnectionData: ",
        "No connections found with connectionID=conn-1");
    assertThatThrownBy(() -> provider.connections().sendMessage(config, "conn-1", map("type", "ka")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void graphQLApiScheduledRocketsAndSensor() throws Exception {
    LocalHttpRequest request = LocalHttpRequest.of("POST", "/", Map.of("Authorization", "Bearer t"),
        map("query", "query { x }"));
    GraphQLRequestEnvelope envelope = (GraphQLRequestEnvelope) provider.graphQL().rawToEnvelope(config, request);
    assertThat(envelope.eventType()).isEqualTo(EventType.MESSAGE);
    assertThat(envelope.token()).isEqualTo("Bearer t");
    assertThat(envelope.value()).isEqualTo(GraphQLOperation.builder().query("query { x }").build());
    assertThat(provider.graphQL().handleResult(map("data", 1), Map.of("h", "v")))
        .isEqualTo(new ApiAdapter.Success(map("data", 1), Map.of("h", "v")));
    assertThat(provider.api().requestFailed(new NotAuthorizedError("no")))
        .isEqualTo(new ApiAdapter.Failure(401, "Not Authorized Error", "no"));
    assertThat(provider.api().healthRequestResult(list(), false))
        .isEqualTo(new ApiAdapter.Success(list(), Map.of("status-code", 503)));
    assertThat(provider.scheduled().rawToEnvelope(config, map("typeName", "Check")).typeName()).isEqualTo("Check");
    assertThat(provider.rockets().rawToEnvelopes(config, map("BOOSTER_ROCKET_FUNCTION_ID", "f")).rocketId())
        .isEqualTo("f");

    assertThat(provider.sensor().isDatabaseEventUp(config)).isFalse();
    assertThat(provider.sensor().databaseEventsHealthDetails(config))
        .isEqualTo(map("file", ".booster/events.json", "count", 0));
    assertThat(provider.sensor().isDatabaseEventUp(config)).isTrue();
    assertThat(provider.sensor().areDatabaseReadModelsUp(config)).isFalse();
    assertThat(provider.sensor().databaseReadModelsHealthDetails(config))
        .isEqualTo(map("file", ".booster/read_models.json", "count", 0));
    assertThat(provider.sensor().areDatabaseReadModelsUp(config)).isTrue();
    assertThat(provider.sensor().databaseUrls(config)).containsExactly(".booster/events.json", ".booster/read_models.json");
    assertThat(provider.sensor().graphQLFunctionUrl(config)).isEqualTo("http://localhost:3000/graphql");
    HealthEnvelope health = provider.sensor().rawRequestToHealthEnvelope(LocalHttpRequest.of("GET",
        "/booster/database", Map.of("authorization", "Bearer h"), null));
    assertThat(health.componentPath()).isEqualTo("booster/database");
    assertThat(health.token()).isEqualTo("Bearer h");
    config.rockets = List.of(new RocketDescriptor("com.example", Map.of("rocketProviderPackage",
        "@boostercloud/rocket-file-uploads-local")));
    assertThat(provider.sensor().areRocketFunctionsUp(config)).isEqualTo(Map.of("rocket-file-uploads", true));
    assertThat(provider.events().rawStreamToEnvelopes(config, null, List.of())).isEmpty();
    assertThat(provider.events().dedupEventStream(config, null)).isEmpty();
    provider.events().produce("Cart", UUID.of("c1"), List.of(), config);
  }

  @Test
  void infrastructureNeedsTheInfrastructureModule() {
    assertThatThrownBy(provider::infrastructure)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("The Local infrastructure package could not be loaded. The following error was "
            + "thrown: Could not load the infrastructure class "
            + "com.boostercloud.framework.provider.local.infrastructure.Infrastructure")
        .hasMessageContaining("com.boostercloud:booster-framework-provider-local-infrastructure");
    List<List<RocketDescriptor>> received = new ArrayList<>();
    LocalProvider withLoader = new LocalProvider(runtime, List.of(new RocketDescriptor("r", null)), packageName -> {
      assertThat(packageName).isEqualTo("com.boostercloud.framework.provider.local.infrastructure");
      return rockets -> {
        received.add(rockets);
        return new com.boostercloud.framework.types.provider.ProviderInfrastructure() { };
      };
    });
    assertThat(withLoader.infrastructure()).isNotNull();
    assertThat(received).containsExactly(List.of(new RocketDescriptor("r", null)));
  }
}
