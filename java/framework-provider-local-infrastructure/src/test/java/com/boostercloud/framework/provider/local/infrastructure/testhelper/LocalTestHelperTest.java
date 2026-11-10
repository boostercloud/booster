package com.boostercloud.framework.provider.local.infrastructure.testhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.local.library.WebSocketServerAdapter;
import com.boostercloud.framework.provider.local.store.Datastore;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.InternalInfo;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Port of {@code test-helper/*} (untested in TypeScript, where everything is a stub): URLs, and real counters and
 * queries over data files in the NeDB format, as the TypeScript and Java providers write them.
 */
class LocalTestHelperTest {

  @TempDir
  Path directory;

  @BeforeEach
  void setUp() throws IOException {
    EnvironmentVariables.unset(InternalInfo.BOOSTER_LOCAL_PORT);
    EnvironmentVariables.unset(WebSocketServerAdapter.LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT);
    Path booster = Files.createDirectories(directory.resolve(".booster"));
    Files.writeString(booster.resolve("events.json"), String.join("\n",
        "{\"kind\":\"event\",\"superKind\":\"domain\",\"entityID\":\"c1\",\"entityTypeName\":\"Cart\","
            + "\"typeName\":\"CartItemChanged\",\"value\":{\"quantity\":1},\"createdAt\":\"2024-01-31T10:00:00.000Z\","
            + "\"_id\":\"AAAAAAAAAAAAAAA1\"}",
        "{\"kind\":\"event\",\"superKind\":\"domain\",\"entityID\":\"c1\",\"entityTypeName\":\"Cart\","
            + "\"typeName\":\"CartItemChanged\",\"value\":{\"quantity\":2},\"createdAt\":\"2024-01-31T10:00:01.000Z\","
            + "\"_id\":\"AAAAAAAAAAAAAAA2\"}",
        "{\"kind\":\"snapshot\",\"superKind\":\"domain\",\"entityID\":\"c1\",\"entityTypeName\":\"Cart\","
            + "\"typeName\":\"Cart\",\"value\":{\"id\":\"c1\"},\"createdAt\":\"2024-01-31T10:00:01.000Z\","
            + "\"persistedAt\":\"2024-01-31T10:00:02.000Z\",\"_id\":\"AAAAAAAAAAAAAAA3\"}",
        "{\"kind\":\"event\",\"superKind\":\"domain\",\"entityID\":\"c2\",\"entityTypeName\":\"Cart\","
            + "\"typeName\":\"CartItemChanged\",\"value\":{\"quantity\":5},\"createdAt\":\"2024-01-31T10:00:03.000Z\","
            + "\"_id\":\"AAAAAAAAAAAAAAA4\"}",
        "{\"kind\":\"event\",\"superKind\":\"domain\",\"entityID\":\"c3\",\"entityTypeName\":\"Cart\","
            + "\"typeName\":\"CartItemChanged\",\"value\":{},\"createdAt\":\"2024-01-31T10:00:04.000Z\","
            + "\"_id\":\"AAAAAAAAAAAAAAA5\"}",
        "{\"$$deleted\":true,\"_id\":\"AAAAAAAAAAAAAAA5\"}",
        "{\"kind\":\"event\",\"superKind\":\"domain\",\"entityID\":\"c2\",\"entityTypeName\":\"Cart\","
            + "\"typeName\":\"CartItemChanged\",\"value\":{},\"deletedAt\":\"2024-02-01T00:00:00.000Z\","
            + "\"createdAt\":\"2024-01-31T10:00:03.000Z\",\"_id\":\"AAAAAAAAAAAAAAA4\"}",
        "{\"$$indexCreated\":{\"fieldName\":\"entityID\",\"unique\":false,\"sparse\":false}}",
        "{\"kind\":\"event\",\"entityID\":\"torn") , StandardCharsets.UTF_8);
    Files.writeString(booster.resolve("read_models.json"), String.join("\n",
        "{\"$$indexCreated\":{\"fieldName\":\"uniqueKey\",\"unique\":true,\"sparse\":true}}",
        "{\"typeName\":\"CartReadModel\",\"value\":{\"id\":\"c1\",\"boosterMetadata\":{\"version\":1,\"schemaVersion\":1}},"
            + "\"uniqueKey\":\"CartReadModel_c1_1\",\"_id\":\"BBBBBBBBBBBBBBB1\"}",
        "{\"typeName\":\"CartReadModel\",\"value\":{\"id\":\"c1\",\"items\":[1],\"boosterMetadata\":{\"version\":2,"
            + "\"schemaVersion\":1}},\"uniqueKey\":\"CartReadModel_c1_2\",\"_id\":\"BBBBBBBBBBBBBBB1\"}",
        "{\"typeName\":\"CartReadModel\",\"value\":{\"id\":\"c2\",\"boosterMetadata\":{\"version\":1,\"schemaVersion\":1}},"
            + "\"uniqueKey\":\"CartReadModel_c2_1\",\"_id\":\"BBBBBBBBBBBBBBB2\"}",
        "{\"typeName\":\"ProductReadModel\",\"value\":{\"id\":\"p1\",\"boosterMetadata\":{\"version\":1,"
            + "\"schemaVersion\":1}},\"uniqueKey\":\"ProductReadModel_p1_1\",\"_id\":\"BBBBBBBBBBBBBBB3\"}",
        ""), StandardCharsets.UTF_8);
    Files.writeString(booster.resolve("connections.json"),
        "{\"expirationTime\":4102444800,\"user\":{\"username\":\"a\"},\"connectionID\":\"k1\",\"_id\":\"CCCCCCCCCCCCCCC1\"}\n",
        StandardCharsets.UTF_8);
    Files.writeString(booster.resolve("subscriptions.json~"), String.join("\n",
        "{\"connectionID\":\"k1\",\"typeName\":\"CartReadModel\",\"expirationTime\":4102444800,"
            + "\"operation\":{\"id\":\"1\"},\"_id\":\"DDDDDDDDDDDDDDD1\"}",
        "{\"connectionID\":\"k1\",\"typeName\":\"CartReadModel\",\"expirationTime\":4102444800,"
            + "\"operation\":{\"id\":\"2\"},\"_id\":\"DDDDDDDDDDDDDDD2\"}", ""), StandardCharsets.UTF_8);
  }

  @AfterEach
  void tearDown() {
    EnvironmentVariables.unset(InternalInfo.BOOSTER_LOCAL_PORT);
    EnvironmentVariables.unset(WebSocketServerAdapter.LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT);
  }

  private LocalTestHelper helper() {
    return LocalTestHelper.build("my-store",
        LocalTestHelper.Options.defaults().withBaseDirectory(directory).withReadinessTimeout(null));
  }

  @Test
  void countsEventsWithoutSnapshotsIncludingSoftDeletedOnes() {
    assertThat(helper().counters().events()).isEqualTo(3);
  }

  @Test
  void countsReadModelsOfOneType() {
    LocalCounters counters = helper().counters();
    assertThat(counters.readModels("CartReadModel")).isEqualTo(2);
    assertThat(counters.readModels("ProductReadModel")).isEqualTo(1);
    assertThat(counters.readModels("Unknown")).isZero();
  }

  @Test
  void countsConnectionsAndSubscriptionsReadingTheCompactionCopyWhenTheFileIsMissing() {
    LocalCounters counters = helper().counters();
    assertThat(counters.connections()).isEqualTo(1);
    assertThat(counters.subscriptions()).isEqualTo(2);
    assertThat(counters.stackName()).isEqualTo("my-store-app");
  }

  @Test
  void missingFilesCountZero() {
    LocalCounters counters = new LocalCounters("x-app", directory.resolve("empty"));
    assertThat(counters.events()).isZero();
    assertThat(counters.connections()).isZero();
    assertThat(counters.subscriptions()).isZero();
    assertThat(counters.readModels("CartReadModel")).isZero();
  }

  @Test
  void queriesEventsByStreamKeyNewestFirst() {
    LocalQueries queries = helper().queries();

    List<Map<String, Object>> events = queries.events("Cart-c1-event");

    assertThat(events).extracting(event -> event.get("_id")).containsExactly("AAAAAAAAAAAAAAA2", "AAAAAAAAAAAAAAA1");
    assertThat(queries.events("Cart-c1-event", false)).extracting(event -> event.get("_id"))
        .containsExactly("AAAAAAAAAAAAAAA1", "AAAAAAAAAAAAAAA2");
    assertThat(queries.events("Cart-c1-snapshot")).extracting(event -> event.get("_id"))
        .containsExactly("AAAAAAAAAAAAAAA3");
    assertThat(queries.events("Cart-c2-event")).singleElement()
        .satisfies(event -> assertThat(event).containsEntry("deletedAt", "2024-02-01T00:00:00.000Z"));
  }

  @Test
  void queriesEveryDocumentOfAnEntityForOtherKeys() {
    assertThat(helper().queries().events("c1")).hasSize(3);
    assertThat(helper().queries().events("unknown")).isEmpty();
  }

  @Test
  void queriesEntitiesWhoseIdsContainDashes() throws IOException {
    Files.writeString(directory.resolve(".booster/events.json"),
        "{\"kind\":\"event\",\"entityID\":\"5f0c-a1-b2\",\"entityTypeName\":\"Cart\",\"createdAt\":\"x\","
            + "\"_id\":\"EEEEEEEEEEEEEEE1\"}\n", StandardCharsets.UTF_8);
    assertThat(new LocalQueries(directory).events("Cart-5f0c-a1-b2-event")).hasSize(1);
  }

  @Test
  void queriesReadModelsById() {
    List<Map<String, Object>> readModels = helper().queries().readModels("c1", "CartReadModel");

    assertThat(readModels).containsExactly(Map.of("id", "c1", "items", List.of(1),
        "boosterMetadata", Map.of("version", 2, "schemaVersion", 1)));
    assertThat(helper().queries().readModels("c1", "ProductReadModel")).isEmpty();
  }

  @Test
  void theStoreReaderReplaysTheFileWithoutModifyingIt() throws IOException {
    Path events = directory.resolve(".booster/events.json");
    String before = Files.readString(events);
    LocalStoreReader reader = new LocalStoreReader(directory);

    assertThat(reader.documents(".booster/events.json")).hasSize(4);
    try (Datastore snapshot = reader.snapshot(".booster/events.json")) {
      assertThat(snapshot.count(Map.of("entityID", "c1"))).isEqualTo(3);
    }
    assertThat(Files.readString(events)).isEqualTo(before);
    assertThat(reader.file(".booster/events.json")).isEqualTo(events);
  }

  @Test
  void outputsUseTheConfiguredPorts() {
    assertThat(helper().outputs()).isEqualTo(new ApplicationOutputs("http://localhost:3000/graphql",
        "ws://localhost:65529/websocket", "http://localhost:3000/sensor/health/"));

    EnvironmentVariables.set(InternalInfo.BOOSTER_LOCAL_PORT, "4000");
    EnvironmentVariables.set(WebSocketServerAdapter.LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT, "4001");
    assertThat(helper().outputs()).isEqualTo(new ApplicationOutputs("http://localhost:4000/graphql",
        "ws://localhost:4001/websocket", "http://localhost:4000/sensor/health/"));

    LocalTestHelper explicit = LocalTestHelper.build("app",
        LocalTestHelper.Options.defaults().withPorts(5000, 5001).withReadinessTimeout(null));
    assertThat(explicit.outputs().graphqlURL()).isEqualTo("http://localhost:5000/graphql");
  }

  @Test
  void buildFailsWhenTheApplicationDoesNotAnswer() throws IOException {
    int port;
    try (ServerSocket probe = new ServerSocket(0)) {
      port = probe.getLocalPort();
    }
    LocalTestHelper.Options options = LocalTestHelper.Options.defaults().withPorts(port, 65529)
        .withReadinessTimeout(Duration.ZERO);

    assertThatThrownBy(() -> LocalTestHelper.build("app", options))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("The local application at http://localhost:" + port + "/graphql is not ready: ");
  }
}
