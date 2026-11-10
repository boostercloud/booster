package com.boostercloud.integration.functionality;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.functionality.StoredDocuments.assertEventStoreKey;
import static com.boostercloud.integration.functionality.StoredDocuments.at;
import static com.boostercloud.integration.functionality.StoredDocuments.object;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/functionality/notifications.integration.ts}: {@code register.flush()}
 * of notifications ({@code CartAbandoned}, whose handler checks the cart), and the stored document of a notification:
 * a regular event on the topic {@code defaultTopic}, keyed by the notification's partition key.
 */
@EndToEnd
@DisplayName("notifications")
class NotificationsFunctionalityIT {

  private static final String FLUSH_NOTIFICATIONS = """
      mutation FlushNotifications($cartId: ID!) {
        FlushNotifications(input: { cartId: $cartId, previousProducts: 1, afterProducts: 3 }) {
          id
          checks
        }
      }
      """;

  private static final String LIST_CART_READ_MODELS = """
      query ListCartReadModels($filter: ListCartReadModelFilter) {
        ListCartReadModels(filter: $filter) {
          items {
            checks
          }
        }
      }
      """;

  private static final String ABANDON_CART = """
      mutation AbandonCart($cartId: ID!) {
        AbandonCart(input: { cartId: $cartId })
      }
      """;

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;

  NotificationsFunctionalityIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    client = applicationUnderTest.graphql().client();
  }

  @Test
  @Order(1)
  @DisplayName("should be persisted when flush is call")
  void shouldBePersistedWhenFlushIsCall() {
    String mockCartId = random.uuid();
    GraphQLResponse result = waitForIt(
        () -> client.mutate(FLUSH_NOTIFICATIONS, Map.of("cartId", mockCartId)),
        response -> response.get("FlushNotifications") instanceof List<?> carts && !carts.isEmpty());

    assertThat(result).isNotNull();
    System.out.println(BoosterJson.toJson(result.toMap()));
    Object previousChecks = result.get("FlushNotifications[0].checks");
    Object afterChecks = result.get("FlushNotifications[1].checks");

    // Events return the cart flushed the first time
    assertThat(previousChecks).as("previous products").isEqualTo(1);

    // Events doesn't return the last 3 events that were not flushed
    assertThat(afterChecks).as("after products").isEqualTo(1);

    System.out.println("Waiting for read model with id " + mockCartId + " to become available");
    GraphQLResponse queryResult = waitForIt(
        () -> client.query(LIST_CART_READ_MODELS, Map.of("filter", object("id", object("eq", mockCartId)))),
        response -> response.get("ListCartReadModels.items[0].checks") instanceof Number checks
            && checks.doubleValue() >= 4);
    System.out.println("Got result " + BoosterJson.toJson(queryResult.toMap()));

    // After the command is executed, the register is flushed, so we will have the 4 cartItems
    assertThat(queryResult.get("ListCartReadModels.items[0].checks")).isEqualTo(4);
  }

  @Test
  @Order(2)
  @DisplayName("should create an event in the event store")
  void shouldCreateAnEventInTheEventStore() {
    System.out.println("Waiting for events to be available");
    long eventsCount = waitForIt(() -> applicationUnderTest.count().events(), count -> count > 0);
    System.out.println("Events count " + eventsCount);

    String mockCartId = random.uuid();
    System.out.println("Abandoning cart " + mockCartId);
    GraphQLResponse response = client.mutate(ABANDON_CART, Map.of("cartId", mockCartId));

    assertThat(response).isNotNull();
    assertThat(response.get("AbandonCart")).isEqualTo(true);

    // Verify number of events
    long expectedEventItemsCount = eventsCount + 1;
    System.out.println("Waiting for events to be available");
    waitForIt(() -> applicationUnderTest.count().events(), newEventsCount -> newEventsCount > eventsCount);
    System.out.println("Events count " + expectedEventItemsCount);

    // Verify latest event
    List<Map<String, Object>> latestEvent =
        applicationUnderTest.query().events("defaultTopic-" + mockCartId + "-event");
    assertThat(latestEvent).isNotNull();
    assertThat(latestEvent).isNotEmpty();

    assertEventStoreKey(latestEvent.get(0), "defaultTopic-" + mockCartId + "-event");
    assertThat(at(latestEvent, "[0].value.something")).isEqualTo(mockCartId);
    assertThat(at(latestEvent, "[0].kind")).isEqualTo("event");
    assertThat(at(latestEvent, "[0].entityTypeName")).isEqualTo("defaultTopic");
    assertThat(at(latestEvent, "[0].typeName")).isEqualTo("CartAbandoned");
  }
}
