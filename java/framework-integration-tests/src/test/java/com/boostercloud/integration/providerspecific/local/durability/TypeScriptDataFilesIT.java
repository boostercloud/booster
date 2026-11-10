package com.boostercloud.integration.providerspecific.local.durability;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.providerspecific.local.durability.Documents.variables;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.integration.harness.AppProcess;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.E2eSettings;
import com.boostercloud.integration.harness.EndToEnd;
import com.boostercloud.integration.helper.FileHelper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Durability case 5 (Java-only, beyond TypeScript): the Java application started on a {@code .booster} directory
 * written by the TypeScript local provider (fixture {@code fixtures/durability/typescript-local-provider}, NeDB format,
 * this application's data shapes; see its README).
 *
 * <p>What it proves: the Java application loads the TypeScript files without reporting a single damaged line; serves
 * their read models (including appended, not compacted, updates and a removed read model), their events through the
 * event search, and their users; continues the entities from the TypeScript snapshots (and reduces from scratch the
 * events TypeScript stored but never projected); continues the TypeScript read-model versions (optimistic concurrency
 * and the {@code uniqueKey} index); and keeps all of it, in the same format, across a restart.
 */
@EndToEnd(EndToEnd.Application.OWN)
@DisplayName("Durability: data files written by the TypeScript local provider")
class TypeScriptDataFilesIT {

  static final String FIXTURE = "durability/typescript-local-provider";
  static final String COUNTER = "7d1e2f30-4a5b-4c6d-8e9f-0a1b2c3d4e5f";
  static final String UNPROJECTED_COUNTER = "5b2c9d4e-1f3a-4b6c-8d7e-9f0a1b2c3d4e";
  static final String CART = "3c9a7b10-2d4e-4f60-9a8b-7c6d5e4f3a21";
  static final String DELETED_CART = "0f8e7d6c-5b4a-4392-8170-6e5d4c3b2a19";

  private final AppProcess app;
  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;
  private StoreFiles store;

  TypeScriptDataFilesIT(AppProcess app, ApplicationUnderTest applicationUnderTest) {
    this.app = app;
    this.applicationUnderTest = applicationUnderTest;
  }

  /** The harness creates the sandbox on the first start: replace its data files with the TypeScript ones. */
  @BeforeAll
  void startOnTheTypeScriptDataFiles() {
    app.stop();
    FileHelper.removeFolders(List.of(app.boosterDirectory()));
    copyFixture(E2eSettings.moduleDirectory().resolve(FileHelper.FIXTURES_DIRECTORY).resolve(FIXTURE),
        app.boosterDirectory());
    app.start();
    client = applicationUnderTest.graphql().client();
    store = StoreFiles.of(app);
  }

  private static void copyFixture(Path fixture, Path booster) {
    try (Stream<Path> files = Files.list(fixture)) {
      Files.createDirectories(booster);
      for (Path file : files.filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
        Files.copy(file, booster.resolve(file.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Map<String, Object> cartItem(String productId, int quantity) {
    return Map.of("__typename", "CartItem", "productId", productId, "quantity", quantity);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> normalizedItems(GraphQLResponse cart) {
    return ((List<Map<String, Object>>) cart.get("CartReadModel.cartItems")).stream()
        .map(item -> cartItem((String) item.get("productId"), ((Number) item.get("quantity")).intValue())).toList();
  }

  @Test
  @Order(1)
  @DisplayName("loads the TypeScript files without reporting a damaged line")
  void loadsTheFilesWithoutWarnings() {
    String output = RecoveryLog.outputOfStart(app.output(), app.name(), 2);
    assertThat(output).contains("Local server listening on")
        .doesNotContain("Discarding an incomplete last line")
        .doesNotContain("corrupt line(s)");
    assertThat(RecoveryLog.of(app, 2).redispatched()).isZero();
  }

  @Test
  @Order(2)
  @DisplayName("serves the TypeScript read models, also the appended updates and the removed ones")
  void servesTheReadModels() {
    GraphQLResponse counter = client.query(Documents.COUNTER_READ_MODEL, variables("id", COUNTER));
    assertThat(counter.get("CounterReadModel.id")).isEqualTo(COUNTER);
    assertThat(counter.get("CounterReadModel.identifier")).isEqualTo("ts-1");
    assertThat(((Number) counter.get("CounterReadModel.amount")).intValue()).isEqualTo(3);

    GraphQLResponse cart = client.query(Documents.CART_READ_MODEL, variables("cartId", CART));
    assertThat(normalizedItems(cart)).containsExactly(cartItem("p1", 2), cartItem("p2", 1));
    assertThat(cart.get("CartReadModel.cartItemsIds")).isEqualTo(List.of("p1", "p2"));
    assertThat(((Number) cart.get("CartReadModel.cartItemsSize")).intValue()).isEqualTo(2);
    assertThat(((Number) cart.get("CartReadModel.checks")).intValue()).isZero();

    assertThat(client.query(Documents.CART_READ_MODEL, variables("cartId", DELETED_CART)).get("CartReadModel"))
        .isNull();
    GraphQLResponse carts = client.query(Documents.LIST_CART_READ_MODELS, variables("filter",
        Map.of("id", Map.of("in", List.of(CART, DELETED_CART, "the-checked-cart")))));
    assertThat(((Number) carts.get("ListCartReadModels.count")).intValue()).isEqualTo(2);
    assertThat(client.query(Documents.COUNTER_READ_MODEL, variables("id", UNPROJECTED_COUNTER))
        .get("CounterReadModel")).isNull();
  }

  @Test
  @Order(3)
  @DisplayName("serves the TypeScript events through the event search, with their users")
  @SuppressWarnings("unchecked")
  void servesTheEvents() {
    List<Map<String, Object>> counterEvents = (List<Map<String, Object>>) client.query(Documents.COUNTER_EVENTS,
        variables("counterId", COUNTER)).get("eventsByEntity");
    assertThat(counterEvents).extracting(event -> event.get("createdAt"))
        .containsExactlyInAnyOrder("2026-11-09T10:00:00.120Z", "2026-11-09T10:00:01.348Z", "2026-11-09T10:00:02.977Z");
    assertThat(counterEvents).allSatisfy(event -> {
      assertThat(event.get("entity")).isEqualTo("Counter");
      assertThat(event.get("entityID")).isEqualTo(COUNTER);
      assertThat(event.get("type")).isEqualTo("CounterAdded");
      assertThat(event.get("user")).isNull();
    });
    assertThat(counterEvents).extracting(event -> ((Map<String, Object>) event.get("value")).get("identifier"))
        .containsExactlyInAnyOrder("ts-1", "ts-2", "ts-3");
    assertThat(counterEvents).extracting(event -> event.get("requestID")).containsExactlyInAnyOrder(
        "1a2b3c4d-0000-4000-8000-000000000001", "1a2b3c4d-0000-4000-8000-000000000002",
        "1a2b3c4d-0000-4000-8000-000000000003");

    List<Map<String, Object>> cartEvents = (List<Map<String, Object>>) client.query(Documents.CART_EVENTS,
        variables("cartId", CART)).get("eventsByEntity");
    assertThat(cartEvents).hasSize(2).allSatisfy(event -> {
      Map<String, Object> user = (Map<String, Object>) event.get("user");
      assertThat(user.get("username")).isEqualTo("ada@example.com");
      assertThat(user.get("roles")).isEqualTo(List.of("UserWithEmail"));
    });
    assertThat(cartEvents).extracting(event -> ((Map<String, Object>) event.get("value")).get("productId"))
        .containsExactlyInAnyOrder("p1", "p2");
  }

  @Test
  @Order(4)
  @DisplayName("continues the TypeScript entities and read-model versions with new commands")
  @SuppressWarnings("unchecked")
  void continuesTheEntitiesAndVersions() {
    int snapshots = store.snapshots("Counter", COUNTER).size();
    assertThat(client.mutate(Documents.INCREMENT_COUNTER, variables("counterId", COUNTER, "identifier", "java-1"))
        .get("IncrementCounter")).isEqualTo(true);
    assertThat(client.mutate(Documents.INCREMENT_COUNTER, variables("counterId", UNPROJECTED_COUNTER,
        "identifier", "java-1")).get("IncrementCounter")).isEqualTo(true);
    assertThat(client.mutate(Documents.CHANGE_CART_ITEM, variables("cartId", CART, "productId", "p3", "quantity", 5))
        .get("ChangeCartItem")).isEqualTo(true);

    waitForIt(() -> client.query(Documents.COUNTER_READ_MODEL, variables("id", COUNTER)),
        response -> response.get("CounterReadModel.amount") instanceof Number amount && amount.intValue() == 4,
        200, 30_000);
    Map<String, Object> counter = store.readModel("CounterReadModel", COUNTER);
    assertThat(counter.get("identifier")).isEqualTo("ts-1");
    assertThat(((Number) ((Map<String, Object>) counter.get("boosterMetadata")).get("version")).intValue())
        .isEqualTo(4);
    assertThat(store.readModels("CounterReadModel")).filteredOn(document -> COUNTER.equals(
        ((Map<String, Object>) document.get("value")).get("id")))
        .singleElement().satisfies(document -> assertThat(document.get("uniqueKey"))
            .isEqualTo("CounterReadModel_" + COUNTER + "_4"));
    assertThat(store.snapshots("Counter", COUNTER)).hasSize(snapshots + 1);

    // reduced from the two TypeScript events nobody projected, plus the new one
    GraphQLResponse unprojected = waitForIt(
        () -> client.query(Documents.COUNTER_READ_MODEL, variables("id", UNPROJECTED_COUNTER)),
        response -> response.get("CounterReadModel.amount") instanceof Number amount && amount.intValue() == 3,
        200, 30_000);
    assertThat(unprojected.get("CounterReadModel.identifier")).isEqualTo("unprojected-1");

    GraphQLResponse cart = waitForIt(() -> client.query(Documents.CART_READ_MODEL, variables("cartId", CART)),
        response -> response.get("CartReadModel.cartItemsSize") instanceof Number size && size.intValue() == 3,
        200, 30_000);
    assertThat(normalizedItems(cart)).containsExactly(cartItem("p1", 2), cartItem("p2", 1), cartItem("p3", 5));
  }

  @Test
  @Order(5)
  @DisplayName("keeps the TypeScript and the Java data, in the NeDB format, across a restart")
  void keepsEverythingAcrossARestart() {
    Map<String, Object> before = Map.of(
        "counter", client.query(Documents.COUNTER_READ_MODEL, variables("id", COUNTER)).toMap(),
        "unprojected", client.query(Documents.COUNTER_READ_MODEL, variables("id", UNPROJECTED_COUNTER)).toMap(),
        "cart", client.query(Documents.CART_READ_MODEL, variables("cartId", CART)).toMap(),
        "events", client.query(Documents.COUNTER_EVENTS, variables("counterId", COUNTER)).toMap());

    app.restart();

    assertThat(Map.of(
        "counter", client.query(Documents.COUNTER_READ_MODEL, variables("id", COUNTER)).toMap(),
        "unprojected", client.query(Documents.COUNTER_READ_MODEL, variables("id", UNPROJECTED_COUNTER)).toMap(),
        "cart", client.query(Documents.CART_READ_MODEL, variables("cartId", CART)).toMap(),
        "events", client.query(Documents.COUNTER_EVENTS, variables("counterId", COUNTER)).toMap()))
        .isEqualTo(before);
    for (String file : List.of(StoreFiles.EVENTS, StoreFiles.READ_MODELS)) {
      assertThat(store.lines(file)).allSatisfy(line -> {
        Map<String, Object> document = BoosterJson.parseObject(line);
        assertThat(document.containsKey("_id") || document.containsKey("$$indexCreated")).as(line).isTrue();
      });
    }
    assertThat(store.lines(StoreFiles.READ_MODELS)).contains(
        "{\"$$indexCreated\":{\"fieldName\":\"uniqueKey\",\"unique\":true,\"sparse\":true}}");
    assertThat(store.events("CounterAdded", COUNTER)).extracting(event -> event.get("_id"))
        .contains("cA1kE9vQ2mXr7TzP", "cA2pW6xN1sZt5UeJ", "cA3qT8zR4dGu2IkL");
  }
}
