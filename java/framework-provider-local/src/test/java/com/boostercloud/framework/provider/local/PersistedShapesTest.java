package com.boostercloud.framework.provider.local;

import static com.boostercloud.framework.provider.local.Raw.list;
import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.local.store.Serialization;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.io.IOException;
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

/**
 * The documents the Java provider writes have the shapes of spec 10, section 4 (the TypeScript provider's): field
 * names, which fields exist, the NeDB markers, and the values derived by the provider ({@code uniqueKey},
 * {@code createdAt}, {@code persistedAt}, {@code deletedAt}, {@code connectionID}).
 */
class PersistedShapesTest {

  @TempDir
  Path project;

  private LocalProviderRuntime runtime;
  private LocalProvider provider;
  private BoosterConfig config;
  private FakeUserApp app;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = Mockito.mock(Logger.class);
    app = new FakeUserApp(config);
    runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(project).withWebSocketServer(false, "0").withUserApp(() -> app));
    provider = new LocalProvider(runtime, List.of(), Provider::loadInfrastructurePackage);
    app.provider(provider);
    Dates.useClock(Clock.fixed(Instant.parse("2026-11-10T09:15:27.412Z"), ZoneOffset.UTC));
  }

  @AfterEach
  void tearDown() {
    runtime.close();
    Dates.resetClock();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> documents(String file) throws IOException {
    List<Map<String, Object>> documents = new ArrayList<>();
    for (String line : Files.readAllLines(project.resolve(".booster").resolve(file))) {
      documents.add((Map<String, Object>) Serialization.deserialize(line));
    }
    return documents;
  }

  private static UserEnvelope ada() {
    return UserEnvelope.builder().username("ada@example.com").roles(List.of("User")).claims(map()).build();
  }

  @Test
  void eventsSnapshotsAndSoftDeletedEvents() throws IOException {
    NonPersistedEventEnvelope event = NonPersistedEventEnvelope.builder()
        .version(1).superKind(SuperKindType.DOMAIN).requestID(UUID.of("req-1")).currentUser(ada())
        .entityID(UUID.of("cart-1")).entityTypeName("Cart").typeName("CartItemChanged")
        .value(map("cartId", "cart-1", "productId", "p-1", "quantity", 2)).build();
    EventEnvelope stored = provider.events().store(List.of(event), config).getFirst();
    Map<String, Object> eventDocument = documents("events.json").getLast();
    assertThat(eventDocument).containsOnlyKeys("version", "kind", "superKind", "requestID", "currentUser", "entityID",
        "entityTypeName", "typeName", "value", "createdAt", "_id");
    assertThat(eventDocument).containsEntry("kind", "event").containsEntry("superKind", "domain")
        .containsEntry("createdAt", "2026-11-10T09:15:27.412Z").containsEntry("_id", stored.id())
        .containsEntry("currentUser", map("username", "ada@example.com", "roles", list("User"), "claims", map()));

    provider.events().storeSnapshot(NonPersistedEntitySnapshotEnvelope.builder()
        .version(1).superKind(SuperKindType.DOMAIN).requestID(UUID.of("req-1")).entityID(UUID.of("cart-1"))
        .entityTypeName("Cart").typeName("Cart").value(map("id", "cart-1", "items", list()))
        .snapshottedEventCreatedAt("2026-11-10T09:15:27.412Z").build(), config);
    Map<String, Object> snapshotDocument = documents("events.json").getLast();
    assertThat(snapshotDocument).containsOnlyKeys("version", "kind", "superKind", "requestID", "entityID",
        "entityTypeName", "typeName", "value", "snapshottedEventCreatedAt", "createdAt", "persistedAt", "_id");
    assertThat(snapshotDocument).containsEntry("kind", "snapshot")
        .containsEntry("createdAt", "2026-11-10T09:15:27.412Z")
        .containsEntry("persistedAt", "2026-11-10T09:15:27.412Z");

    Dates.useClock(Clock.fixed(Instant.parse("2026-11-11T00:00:00.000Z"), ZoneOffset.UTC));
    EventDeleteParameters parameters = new EventDeleteParameters("Cart", UUID.of("cart-1"), "2026-11-10T09:15:27.412Z");
    provider.events().deleteEvent(config, provider.events().findDeletableEvent(config, parameters));
    Map<String, Object> deleted = documents("events.json").getLast();
    // Q17: the soft-deleted document keeps every field, gets an empty value, the deletion date and an id
    assertThat(deleted).containsOnlyKeys("version", "kind", "superKind", "requestID", "currentUser", "entityID",
        "entityTypeName", "typeName", "value", "createdAt", "deletedAt", "id", "_id");
    assertThat(deleted).containsEntry("value", map()).containsEntry("deletedAt", "2026-11-11T00:00:00.000Z")
        .containsEntry("id", stored.id()).containsEntry("_id", stored.id());
  }

  @Test
  void readModelsCarryTheirUniqueKey() throws IOException {
    provider.readModels().store(config, "CartReadModel",
        map("id", "cart-1", "items", list(), "boosterMetadata", map("version", 1, "schemaVersion", 1)), 0L);
    provider.readModels().store(config, "CartReadModel",
        map("id", "cart-1", "items", list("x"), "boosterMetadata", map("version", 2, "schemaVersion", 1)), 1L);
    List<Map<String, Object>> documents = documents("read_models.json");
    assertThat(documents.getFirst()).isEqualTo(map("$$indexCreated",
        map("fieldName", "uniqueKey", "unique", true, "sparse", true)));
    Map<String, Object> first = documents.get(1);
    Map<String, Object> second = documents.get(2);
    assertThat(List.copyOf(first.keySet())).containsExactly("typeName", "value", "uniqueKey", "_id");
    assertThat(first).containsEntry("uniqueKey", "CartReadModel_cart-1_1");
    assertThat(List.copyOf(second.keySet())).containsExactly("typeName", "value", "uniqueKey", "_id");
    // the compare-and-swap replaces the document: same _id, new version and unique key
    assertThat(second).containsEntry("uniqueKey", "CartReadModel_cart-1_2").containsEntry("_id", first.get("_id"));
  }

  @Test
  void connectionsAndSubscriptions() throws IOException {
    provider.connections().storeData(config, "conn-1", new ConnectionDataEnvelope(1763371527, ada()));
    List<Map<String, Object>> connections = documents("connections.json");
    // the TTL index line keeps NeDB's options as given
    assertThat(connections.getFirst()).isEqualTo(map("$$indexCreated",
        map("fieldName", "expirationTime", "expireAfterSeconds", 172800)));
    assertThat(List.copyOf(connections.get(1).keySet()))
        .containsExactly("expirationTime", "user", "connectionID", "_id");
    assertThat(connections.get(1)).containsEntry("expirationTime", 1763371527).containsEntry("connectionID", "conn-1");

    ReadModelRequestEnvelope request = ReadModelRequestEnvelope.builder().requestID(UUID.of("req-7"))
        .currentUser(ada()).className("CartReadModel").version(1).filters(map("id", map("eq", "cart-1"))).build();
    provider.readModels().subscribe(config, SubscriptionEnvelope.of(request, 1762939527, "conn-1",
        GraphQLOperation.builder().id("1").query("subscription { CartReadModel(id: \"cart-1\") { id } }")
            .variables(map()).build()));
    Map<String, Object> subscription = documents("subscriptions.json").getLast();
    assertThat(subscription).containsKeys("requestID", "currentUser", "className", "filters", "version",
        "expirationTime", "connectionID", "operation", "_id");
    assertThat(subscription).containsEntry("className", "CartReadModel").containsEntry("connectionID", "conn-1")
        .containsEntry("expirationTime", 1762939527).containsEntry("filters", map("id", map("eq", "cart-1")))
        .containsEntry("operation", map("query", "subscription { CartReadModel(id: \"cart-1\") { id } }", "id", "1",
            "variables", map()));
    // no Java-only fields leak into the document
    assertThat(subscription.keySet()).isSubsetOf("requestID", "currentUser", "className", "filters", "sortBy",
        "limit", "afterCursor", "paginatedVersion", "version", "select", "expirationTime", "connectionID",
        "operation", "_id", "key", "context");
  }

  @Test
  void healthCountsEveryDocumentIncludingSnapshotsAndSoftDeletedEvents() {
    NonPersistedEventEnvelope event = NonPersistedEventEnvelope.builder()
        .version(1).superKind(SuperKindType.DOMAIN).requestID(UUID.of("req-1")).entityID(UUID.of("cart-1"))
        .entityTypeName("Cart").typeName("CartItemChanged").value(map()).build();
    provider.events().store(List.of(event, event), config);
    provider.events().storeSnapshot(NonPersistedEntitySnapshotEnvelope.builder()
        .version(1).superKind(SuperKindType.DOMAIN).requestID(UUID.of("req-1")).entityID(UUID.of("cart-1"))
        .entityTypeName("Cart").typeName("Cart").value(map("id", "cart-1"))
        .snapshottedEventCreatedAt("2026-11-10T09:15:27.412Z").build(), config);
    EventDeleteParameters parameters = new EventDeleteParameters("Cart", UUID.of("cart-1"), "2026-11-10T09:15:27.412Z");
    provider.events().deleteEvent(config, provider.events().findDeletableEvent(config, parameters));
    assertThat(provider.sensor().databaseEventsHealthDetails(config))
        .isEqualTo(map("file", ".booster/events.json", "count", 3));
    provider.readModels().store(config, "A", map("id", "1", "boosterMetadata", map("version", 1)), 0L);
    provider.readModels().store(config, "B", map("id", "1", "boosterMetadata", map("version", 1)), 0L);
    assertThat(provider.sensor().databaseReadModelsHealthDetails(config))
        .isEqualTo(map("file", ".booster/read_models.json", "count", 2));
  }
}
