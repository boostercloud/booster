package com.boostercloud.integration.providerspecific.local.durability;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.providerspecific.local.durability.Documents.variables;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.DisconnectableGraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.harness.AppProcess;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import com.boostercloud.integration.harness.Faker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Durability case 1 (Java-only, beyond TypeScript): a graceful restart ({@code SIGTERM}, then a new process on the
 * same project directory).
 *
 * <p>What it proves: the process ends through its graceful shutdown (exit code 143, {@code Local server stopped});
 * every event, entity snapshot and read model document is on disk afterwards exactly as before (data written by
 * commands, by a data migration and by a product with two read models); the data-migration state survives (a second
 * {@code MigrateCommand} does not run the finished migrations again); every GraphQL answer (read models by id and by
 * filter, event search) is identical before and after; reduction continues from the stored snapshots; and a
 * subscription client that was connected through the restart reconnects by itself (it re-sends its subscription on a
 * new connection) and keeps receiving notifications. The scheduled command is covered by
 * {@link ScheduledCommandsAfterRestartIT}.
 */
@EndToEnd(EndToEnd.Application.OWN)
@DisplayName("Durability: graceful restart (SIGTERM)")
class GracefulRestartIT {

  private final AppProcess app;
  private final ApplicationUnderTest applicationUnderTest;
  private final String counterA = Faker.random.uuid();
  private final String counterB = Faker.random.uuid();
  private final String subscribedCounter = Faker.random.uuid();
  private final String cartId = Faker.random.uuid();
  private final String migratedCartId = Faker.random.uuid();
  private final String productId = Faker.random.uuid();
  private final String sku = "durable-" + Faker.random.alphaNumeric(8);
  private final List<GraphQLResponse> notifications = new CopyOnWriteArrayList<>();
  private final AtomicInteger reconnections = new AtomicInteger();
  private GraphQLClient client;
  private GraphQLClient userClient;
  private DisconnectableGraphQLClient subscriptionClient;
  private StoreFiles store;
  private Map<String, Object> answersBefore;
  private Map<String, Map<String, Object>> eventsBefore;
  private Map<String, Map<String, Object>> readModelsBefore;
  private Set<String> connectionsBefore;
  private List<String> subscriptionsAfterTheStop;
  private int exitCode;

  GracefulRestartIT(AppProcess app, ApplicationUnderTest applicationUnderTest) {
    this.app = app;
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void writeData() {
    client = applicationUnderTest.graphql().client();
    userClient = applicationUnderTest.graphql().client(
        applicationUnderTest.token().forUser(Faker.internet.email(), "UserWithEmail"));
    store = StoreFiles.of(app);

    for (int i = 0; i < 3; i++) {
      increment(counterA, "a-" + i);
    }
    increment(counterB, "b-0");
    mutate(Documents.CHANGE_CART_ITEM, "ChangeCartItem", variables("cartId", cartId, "productId", "p1", "quantity", 2));
    mutate(Documents.CHANGE_CART_ITEM, "ChangeCartItem", variables("cartId", cartId, "productId", "p2", "quantity", 3));
    assertThat(userClient.mutate(Documents.CREATE_PRODUCT, variables("sku", sku, "productID", productId,
        "priceInCents", 4200)).get("CreateProduct")).isEqualTo(true);

    // a data migration: CartIdDataMigrateV1 then V2 move this cart to the MigratedCart NEW_CART_IDS[0]
    mutate(Documents.CHANGE_CART_ITEM, "ChangeCartItem", variables("cartId", migratedCartId, "productId", "pm",
        "quantity", Constants.QUANTITY_TO_MIGRATE_DATA));
    waitForIt(() -> client.query(Documents.CART_READ_MODEL, variables("cartId", migratedCartId)),
        response -> response.get("CartReadModel") != null);
    mutate(Documents.MIGRATE_COMMAND, "MigrateCommand", Map.of());
    waitForIt(() -> client.query(Documents.LIST_DATA_MIGRATIONS),
        response -> LoadConsistency.accept(statuses(response).equals(Map.of("CartIdDataMigrateV1", "1",
            "CartIdDataMigrateV2", "1")), "migrations: " + response.toMap()));
    waitForIt(() -> client.query(Documents.CART_READ_MODEL, variables("cartId", Constants.NEW_CART_IDS.getFirst())),
        response -> firstItem(response) instanceof Map<?, ?> item && "pm".equals(item.get("productId"))
            && item.get("quantity") instanceof Number quantity
            && quantity.intValue() == Constants.QUANTITY_AFTER_DATA_MIGRATION_V2, 500, 60_000);

    subscriptionClient = applicationUnderTest.graphql().clientWithSubscriptions();
    subscriptionClient.websocket().onReconnected(payload -> reconnections.incrementAndGet());
    subscriptionClient.subscribe(Documents.COUNTER_SUBSCRIPTION, variables("id", subscribedCounter))
        .subscribe(notifications::add);
    waitForIt(() -> subscriptionsOf(subscribedCounter).size(), count -> count == 1);
    connectionsBefore = Set.copyOf(subscriptionsOf(subscribedCounter));
    increment(subscribedCounter, "s-0");
    waitForIt(this::amounts, amounts -> amounts.contains(1));

    answersBefore = answers();
    eventsBefore = byId(store.documents(StoreFiles.EVENTS));
    readModelsBefore = byId(store.documents(StoreFiles.READ_MODELS));
  }

  @AfterAll
  void disconnect() {
    if (subscriptionClient != null) {
      subscriptionClient.disconnect();
    }
  }

  private void increment(String counterId, String identifier) {
    mutate(Documents.INCREMENT_COUNTER, "IncrementCounter", variables("counterId", counterId, "identifier",
        identifier));
  }

  private void mutate(String document, String field, Map<String, Object> variables) {
    assertThat(client.mutate(document, variables).get(field)).as(field).isEqualTo(true);
  }

  @SuppressWarnings("unchecked")
  private static Object firstItem(GraphQLResponse cart) {
    Object items = cart.get("CartReadModel.cartItems");
    return items instanceof List<?> list && !list.isEmpty() ? ((List<Object>) list).getFirst() : null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> statuses(GraphQLResponse migrations) {
    Object items = migrations.get("ListDataMigrationsReadModels.items");
    Map<String, Object> statuses = new LinkedHashMap<>();
    if (items instanceof List<?> list) {
      for (Object item : list) {
        Map<String, Object> migration = (Map<String, Object>) item;
        statuses.put(String.valueOf(migration.get("id")), migration.get("status"));
      }
    }
    return statuses;
  }

  /** The connection ids of the stored subscriptions to a counter. */
  @SuppressWarnings("unchecked")
  private List<String> subscriptionsOf(String counterId) {
    return store.find(StoreFiles.SUBSCRIPTIONS, StoreFiles.query("className", "CounterReadModel")).stream()
        .filter(subscription -> String.valueOf(subscription.get("operation")).contains("CounterReadModel")
            && counterId.equals(((Map<String, Object>) ((Map<String, Object>) subscription.get("operation"))
                .get("variables")).get("id")))
        .map(subscription -> String.valueOf(subscription.get("connectionID")))
        .toList();
  }

  private List<Integer> amounts() {
    return notifications.stream()
        .map(notification -> notification.get("CounterReadModel.amount"))
        .filter(Number.class::isInstance)
        .map(amount -> ((Number) amount).intValue())
        .toList();
  }

  /** Everything this suite reads through GraphQL. */
  private Map<String, Object> answers() {
    Map<String, Object> answers = new LinkedHashMap<>();
    for (String counterId : List.of(counterA, counterB, subscribedCounter)) {
      answers.put("counter " + counterId, client.query(Documents.COUNTER_READ_MODEL, variables("id", counterId))
          .toMap());
    }
    answers.put("counter events", client.query(Documents.COUNTER_EVENTS, variables("counterId", counterA)).toMap());
    answers.put("cart", client.query(Documents.CART_READ_MODEL, variables("cartId", cartId)).toMap());
    answers.put("cart events", client.query(Documents.CART_EVENTS, variables("cartId", cartId)).toMap());
    answers.put("carts", client.query(Documents.LIST_CART_READ_MODELS, variables("filter",
        Map.of("id", Map.of("in", List.of(cartId, migratedCartId, Constants.NEW_CART_IDS.getFirst()))))).toMap());
    answers.put("migrated cart", client.query(Documents.CART_READ_MODEL,
        variables("cartId", Constants.NEW_CART_IDS.getFirst())).toMap());
    answers.put("migrations", client.query(Documents.LIST_DATA_MIGRATIONS).toMap());
    answers.put("product", userClient.query(Documents.PRODUCT_READ_MODEL, variables("id", productId)).toMap());
    answers.put("products by sku", client.query(Documents.PRODUCTS_BY_SKU, variables("sku", sku)).toMap());
    return answers;
  }

  /** Documents by {@code _id}, without those of the scheduled command, which keeps writing every minute. */
  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, Object>> byId(List<Map<String, Object>> documents) {
    return documents.stream()
        .filter(document -> !"the-checked-cart".equals(document.get("entityID"))
            && !(document.get("value") instanceof Map<?, ?> value && "the-checked-cart".equals(value.get("id"))))
        .collect(Collectors.toMap(document -> String.valueOf(document.get("_id")), document -> document,
            (first, second) -> second, LinkedHashMap::new));
  }

  @Test
  @Order(1)
  @DisplayName("stops through its graceful shutdown and starts again on the same data")
  void stopsGracefullyAndStartsAgain() {
    exitCode = app.stop();
    String stopOutput = RecoveryLog.outputOfStart(app.output(), app.name(), 1);
    subscriptionsAfterTheStop = subscriptionsOf(subscribedCounter);
    app.start();

    assertThat(exitCode).isEqualTo(AppProcess.SIGTERM_EXIT_CODE);
    assertThat(stopOutput).contains("Stopping the local server on http://localhost:" + app.httpPort())
        .contains("Local server stopped")
        .doesNotContain("Error handling the websocket DISCONNECT event");
    RecoveryLog recovery = RecoveryLog.of(app, 2);
    assertThat(recovery.interruptedEventIds()).isEmpty();
    assertThat(recovery.redispatched()).isZero();
  }

  @Test
  @Order(2)
  @DisplayName("keeps every event and entity snapshot exactly as it was")
  void keepsEveryEventAndSnapshot() {
    Map<String, Map<String, Object>> eventsAfter = byId(store.documents(StoreFiles.EVENTS));
    assertThat(eventsAfter).isEqualTo(eventsBefore);
    assertThat(store.snapshots("Counter", counterA)).isNotEmpty();
    assertThat(store.snapshots("Product", productId)).isNotEmpty();
    assertThat(eventsAfter.values()).filteredOn(event -> "BoosterDataMigrationEntity".equals(event.get(
        "entityTypeName")) && "snapshot".equals(event.get("kind"))).isNotEmpty();
  }

  @Test
  @Order(3)
  @DisplayName("keeps every read model, including the data-migration state")
  void keepsEveryReadModel() {
    assertThat(byId(store.documents(StoreFiles.READ_MODELS))).isEqualTo(readModelsBefore);
    long migrationEvents = store.find(StoreFiles.EVENTS, StoreFiles.query("entityTypeName",
        "BoosterDataMigrationEntity")).size();

    mutate(Documents.MIGRATE_COMMAND, "MigrateCommand", Map.of());

    assertThat(store.find(StoreFiles.EVENTS, StoreFiles.query("entityTypeName", "BoosterDataMigrationEntity")))
        .as("finished migrations are not run again").hasSize((int) migrationEvents);
    assertThat(statuses(client.query(Documents.LIST_DATA_MIGRATIONS)))
        .isEqualTo(Map.of("CartIdDataMigrateV1", "1", "CartIdDataMigrateV2", "1"));
  }

  @Test
  @Order(4)
  @DisplayName("answers every GraphQL query exactly as before the restart")
  void answersEveryQueryAsBefore() {
    assertThat(answers()).isEqualTo(answersBefore);
  }

  @Test
  @Order(5)
  @DisplayName("keeps reducing the entities from their stored snapshots")
  void keepsReducingFromTheStoredSnapshots() {
    int snapshots = store.snapshots("Counter", counterA).size();
    increment(counterA, "a-after");
    GraphQLResponse counter = waitForIt(() -> client.query(Documents.COUNTER_READ_MODEL, variables("id", counterA)),
        response -> response.get("CounterReadModel.amount") instanceof Number amount && amount.intValue() == 4);
    assertThat(counter.get("CounterReadModel.identifier")).isEqualTo("a-0");
    assertThat(store.snapshots("Counter", counterA)).hasSize(snapshots + 1);
    @SuppressWarnings("unchecked")
    Map<String, Object> metadata = (Map<String, Object>) store.readModel("CounterReadModel", counterA)
        .get("boosterMetadata");
    assertThat(((Number) metadata.get("version")).intValue()).isEqualTo(4);
  }

  @Test
  @Order(6)
  @DisplayName("reconnects the subscription client, which keeps receiving notifications")
  void reconnectsTheSubscriptionClient() {
    // the graceful stop processed the DISCONNECT of the open connection: its subscription is gone
    assertThat(subscriptionsAfterTheStop).isEmpty();
    waitForIt(reconnections::get, count -> count >= 1, 200, 60_000);
    List<String> resubscribed = waitForIt(() -> subscriptionsOf(subscribedCounter),
        connections -> connections.stream().anyMatch(connection -> !connectionsBefore.contains(connection)),
        200, 60_000);
    assertThat(resubscribed).isNotEmpty();

    increment(subscribedCounter, "s-after");

    List<Integer> amounts = waitForIt(this::amounts, received -> received.contains(2), 200, 60_000);
    assertThat(amounts).containsSubsequence(1, 2);
    assertThat(subscriptionsOf(subscribedCounter)).hasSize(1);
    assertThat(RecoveryLog.outputOfStart(app.output(), app.name(), 2))
        .doesNotContain("There is no open websocket connection");
  }
}
