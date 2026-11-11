package com.boostercloud.framework.provider.local;

import static com.boostercloud.framework.provider.local.Raw.list;
import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.local.store.Serialization;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.EventParametersFilterByEntity;
import com.boostercloud.framework.types.envelope.EventParametersFilterByType;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/**
 * Reads data files written by the TypeScript local provider (NeDB format, see {@code src/test/resources/nedb}) and
 * checks that the Java provider sees the same data, keeps working on them and writes lines in the same format.
 */
class NedbFixtureCompatibilityTest {

  @TempDir
  Path project;

  private LocalProviderRuntime runtime;
  private LocalProvider provider;
  private BoosterConfig config;

  @BeforeEach
  void setUp() throws IOException, URISyntaxException {
    Path fixture = Path.of(getClass().getResource("/nedb/typescript-app/dot-booster").toURI());
    Path booster = Files.createDirectories(project.resolve(".booster"));
    try (Stream<Path> files = Files.list(fixture)) {
      for (Path file : files.toList()) {
        Files.copy(file, booster.resolve(file.getFileName().toString()));
      }
    }
    config = new BoosterConfig("test");
    config.logger = Mockito.mock(Logger.class);
    FakeUserApp app = new FakeUserApp(config);
    runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(project).withWebSocketServer(false, "0").withUserApp(() -> app));
    provider = new LocalProvider(runtime, List.of(), Provider::loadInfrastructurePackage);
    app.provider(provider);
  }

  @AfterEach
  void tearDown() {
    runtime.close();
  }

  private List<String> lines(String file) throws IOException {
    return Files.readAllLines(project.resolve(".booster").resolve(file));
  }

  @Test
  void readsEventsAndSnapshotsWrittenByTypeScript() {
    List<EventEnvelope> events = provider.events().forEntitySince(config, "Cart", UUID.of("cart-1"));
    assertThat(events).extracting(EventEnvelope::requestID)
        .containsExactly(UUID.of("req-1"), UUID.of("req-2"), UUID.of("req-4"));
    assertThat(events.get(0).currentUser().username()).isEqualTo("ada@example.com");
    assertThat(events.get(0).superKind()).isEqualTo(SuperKindType.DOMAIN);
    assertThat(events.get(0).id()).isEqualTo("Xk2pQ9aLm0TzB7cd");
    // NeDB dates inside values are read as dates and handed to core as ISO strings
    assertThat(events.get(2).value()).isEqualTo(map("cartId", "cart-1", "at", "2025-11-10T09:20:00.000Z"));
    assertThat(provider.events().forEntitySince(config, "Cart", UUID.of("cart-1"), "2026-11-10T09:15:28.000Z"))
        .extracting(EventEnvelope::typeName).containsExactly("CartChecked");
    // the soft-deleted event of cart-2 is not reduced
    assertThat(provider.events().forEntitySince(config, "Cart", UUID.of("cart-2"))).isEmpty();

    EntitySnapshotEnvelope snapshot = provider.events().latestEntitySnapshot(config, "Cart", UUID.of("cart-1"))
        .orElseThrow();
    assertThat(snapshot.snapshottedEventCreatedAt()).isEqualTo("2026-11-10T09:15:28.000Z");
    assertThat(snapshot.persistedAt()).isEqualTo("2026-11-10T09:15:28.050Z");
    assertThat(provider.events().latestEntitySnapshot(config, "Cart", UUID.of("cart-2"))).isEmpty();
  }

  @Test
  void searchesTypeScriptEvents() {
    List<EventSearchResponse> byEntity = provider.events().search(config,
        EventParametersFilterByEntity.builder().entity("Cart").build());
    assertThat(byEntity).extracting(EventSearchResponse::createdAt).containsExactly(
        "2026-11-10T09:20:00.000Z", "2026-11-10T09:16:00.000Z", "2026-11-10T09:15:28.000Z", "2026-11-10T09:15:27.412Z");
    assertThat(byEntity.get(1).deletedAt()).isEqualTo("2026-11-10T10:00:00.000Z");
    assertThat(byEntity.get(1).value()).isEqualTo(map());
    List<EventSearchResponse> byType = provider.events().search(config,
        new EventParametersFilterByType("ProductCreated", null, null, null));
    assertThat(byType).hasSize(1);
    assertThat(byType.get(0).value()).isEqualTo(map("sku", "SKU-1", "description", "café   line"));

    PaginatedEntitiesIdsResult ids = provider.events().searchEntitiesIDs(config, 10, null, "Cart");
    assertThat(ids.items()).extracting(item -> item.entityID().value()).containsExactly("cart-1");
    assertThat(ids.cursor()).isEqualTo(Map.of("id", "10"));
  }

  @Test
  void continuesTheReadModelsOfTypeScriptWithOptimisticConcurrency() throws IOException {
    List<Map<String, Object>> fetched = provider.readModels().fetch(config, "CartReadModel", UUID.of("cart-1"), null);
    assertThat(fetched).hasSize(1);
    assertThat(fetched.get(0)).containsEntry("checks", 1);
    Map<String, Object> next = map("id", "cart-1", "cartItems", list(), "checks", 2,
        "boosterMetadata", map("version", 3, "schemaVersion", 1));
    assertThatThrownBy(() -> provider.readModels().store(config, "CartReadModel", next, 1L))
        .isInstanceOf(OptimisticConcurrencyUnexpectedVersionError.class)
        .hasMessageStartingWith("Can't update readModel {\"typeName\":\"CartReadModel\"")
        .hasMessageEndingWith("with expectedCurrentVersion = 1 . Optimistic concurrency error");
    provider.readModels().store(config, "CartReadModel", next, 2L);
    assertThat(provider.readModels().fetch(config, "CartReadModel", UUID.of("cart-1"), null).get(0))
        .containsEntry("checks", 2);
    assertThat(lines("read_models.json")).last().isEqualTo(
        "{\"typeName\":\"CartReadModel\",\"value\":{\"id\":\"cart-1\",\"cartItems\":[],\"checks\":2,"
            + "\"boosterMetadata\":{\"version\":3,\"schemaVersion\":1}},\"uniqueKey\":\"CartReadModel_cart-1_3\","
            + "\"_id\":\"Qw3eRt5yUi7oPa9s\"}");
    // the unique index restored from the TypeScript file still applies
    Map<String, Object> duplicate = map("id", "cart-2", "boosterMetadata", map("version", 1, "schemaVersion", 1));
    assertThatThrownBy(() -> provider.readModels().store(config, "CartReadModel", duplicate, null))
        .isInstanceOf(OptimisticConcurrencyUnexpectedVersionError.class)
        .hasMessage("Can't insert key \"CartReadModel_cart-2_1\", it violates the unique constraint");

    assertThat(provider.readModels().search(config, "ProductReadModel",
        map("tags", map("includes", "eco")), null, null, null, false, null).items())
        .extracting(rm -> rm.get("id")).containsExactly("p-1");
    assertThat(provider.readModels().search(config, "CartReadModel", map(), map("checks", "DESC"), null, null, false,
        List.of("id", "checks")).items()).containsExactly(map("id", "cart-2", "checks", 3), map("id", "cart-1",
            "checks", 2));
  }

  @Test
  void readsConnectionsAndSubscriptionsOfTypeScript() {
    ConnectionDataEnvelope data = provider.connections().fetchData(config, "5f0c2f0e-3b5a-4a39-8f2e-0c7b2c0f9a11")
        .orElseThrow();
    assertThat(data.expirationTime()).isEqualTo(1763371527L);
    assertThat(data.user().username()).isEqualTo("ada@example.com");
    List<SubscriptionEnvelope> subscriptions = provider.readModels().fetchSubscriptions(config, "CartReadModel");
    assertThat(subscriptions).extracting(SubscriptionEnvelope::connectionID)
        .containsExactly("5f0c2f0e-3b5a-4a39-8f2e-0c7b2c0f9a11", "other-connection");
    assertThat(subscriptions.get(0).filters()).isEqualTo(map("id", map("eq", "cart-1")));
    assertThat(subscriptions.get(0).operation().id()).isEqualTo("1");
    provider.readModels().deleteSubscription(config, "5f0c2f0e-3b5a-4a39-8f2e-0c7b2c0f9a11", "1");
    assertThat(provider.readModels().fetchSubscriptions(config, "CartReadModel")).hasSize(1);
  }

  @Test
  void loadingCompactsTheFilesTheWayNedbDoes() throws IOException {
    provider.events().forEntitySince(config, "Cart", UUID.of("cart-1"));
    provider.readModels().fetch(config, "CartReadModel", UUID.of("cart-1"), null);
    provider.connections().fetchData(config, "x");
    List<String> events = lines("events.json");
    // one line per live document, in _id order; the deleted snapshot is gone, the soft-deleted event kept
    assertThat(events).hasSize(6);
    assertThat(events).noneMatch(line -> line.contains("S2nApShOtXxYyZz0"));
    assertThat(events.get(0)).contains("\"_id\":\"0aaaaaaaaaaaaaaa\"");
    assertThat(events).anyMatch(line -> line.contains("\"deletedAt\":\"2026-11-10T10:00:00.000Z\""));
    assertThat(events).anyMatch(line -> line.contains("\"at\":{\"$$date\":1762766400000}"));
    assertThat(lines("read_models.json")).last()
        .isEqualTo("{\"$$indexCreated\":{\"fieldName\":\"uniqueKey\",\"unique\":true,\"sparse\":true}}");
    // NeDB rewrites index definitions without their TTL
    assertThat(lines("connections.json")).last()
        .isEqualTo("{\"$$indexCreated\":{\"fieldName\":\"expirationTime\",\"unique\":false,\"sparse\":false}}");
  }

  @Test
  void writesEventsInTheTypeScriptShape() throws IOException {
    NonPersistedEventEnvelope event = NonPersistedEventEnvelope.builder()
        .version(1).superKind(SuperKindType.DOMAIN).entityID(UUID.of("cart-3")).requestID(UUID.of("req-9"))
        .entityTypeName("Cart").typeName("CartItemChanged").value(map("cartId", "cart-3", "quantity", 1.0))
        .build();
    List<EventEnvelope> stored = provider.events().store(List.of(event), config);
    String line = lines("events.json").getLast();
    Map<String, Object> document = (Map<String, Object>) Serialization.deserialize(line);
    assertThat(List.copyOf(document.keySet())).containsExactly("version", "kind", "superKind", "entityID",
        "requestID", "entityTypeName", "typeName", "value", "createdAt", "_id");
    assertThat(document.get("_id")).isEqualTo(stored.get(0).id());
    assertThat(line).contains("\"quantity\":1}");
    assertThat((String) document.get("createdAt")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
    // the new event sorts after the TypeScript ones
    assertThat(provider.events().forEntitySince(config, "Cart", UUID.of("cart-3"))).hasSize(1);
  }
}
