package com.boostercloud.integration.functionality;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.functionality.StoredDocuments.assertEventStoreKey;
import static com.boostercloud.integration.functionality.StoredDocuments.at;
import static com.boostercloud.integration.functionality.StoredDocuments.object;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/functionality/events.integration.ts}: {@code register.flush()} inside a
 * command, and the event documents one command and many commands' worth of events leave in the event store, read
 * from the provider ({@code count().events()}, {@code query().events(key)}, real on the local provider).
 *
 * <p>TypeScript checks the order of the stored events with chai-arrays' {@code sorted((prev, next) => prev > next)}.
 * A boolean comparator never returns a negative number, so V8's sort leaves any array as it is and that assertion
 * always passes; the port asserts what it means (newest first, so the product numbers strictly descend), as the
 * layout contract requires for assertions that pass silently. The exact count of {@code should create an event in the
 * event store} leaves out the events of the scheduled command (see {@link TheCheckedCart}).
 */
@EndToEnd
@DisplayName("events")
class EventsFunctionalityIT {

  private static final String FLUSH_EVENTS = """
      mutation FlushEvents($cartId: ID!) {
        FlushEvents(input: { cartId: $cartId, previousProducts: 1, afterProducts: 3 }) {
          id
          cartItems {
            productId
            quantity
          }
        }
      }
      """;

  private static final String LIST_CART_READ_MODELS = """
      query ListCartReadModels($filter: ListCartReadModelFilter) {
        ListCartReadModels(filter: $filter) {
          items {
            id
            cartItems {
              productId
              quantity
            }
          }
        }
      }
      """;

  private static final String CHANGE_CART_ITEM = """
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }
      """;

  private static final String CHANGE_MULTIPLE_CART_ITEMS = """
      mutation ChangeMultipleCartItems($cartId: ID!, $itemsCount: Float!) {
        ChangeMultipleCartItems(input: { cartId: $cartId, itemsCount: $itemsCount })
      }
      """;

  private static final String CHANGE_MULTIPLE_CART_ITEMS_WITH_IDS = """
      mutation ChangeMultipleCartItemsWithIds($items: [JSON!]!) {
        ChangeMultipleCartItemsWithIds(input: { items: $items })
      }
      """;

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;

  EventsFunctionalityIT(ApplicationUnderTest applicationUnderTest) {
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
        () -> client.mutate(FLUSH_EVENTS, Map.of("cartId", mockCartId)),
        response -> response != null && response.get("FlushEvents") instanceof List<?> carts && !carts.isEmpty());

    assertThat(result).isNotNull();
    List<?> previousProducts = (List<?>) result.get("FlushEvents[0].cartItems");
    List<?> afterProducts = (List<?>) result.get("FlushEvents[1].cartItems");

    // Events return the cart flushed the first time
    assertThat(previousProducts).hasSize(1);

    // Events doesn't return the last 3 events that were not flushed
    assertThat(afterProducts).hasSize(1);

    GraphQLResponse queryResult = waitForIt(
        () -> client.query(LIST_CART_READ_MODELS, Map.of("filter", object("id", object("eq", mockCartId)))),
        response -> response.get("ListCartReadModels.items") instanceof List<?> items && items.size() == 1
            && response.get("ListCartReadModels.items[0].cartItems") instanceof List<?> cartItems
            && cartItems.size() == 4);

    // After the command is executed, the register is flushed, so we will have the 4 cartItems
    assertThat((List<?>) queryResult.get("ListCartReadModels.items[0].cartItems")).hasSize(4);
  }

  @Test
  @Order(2)
  @DisplayName("should create an event in the event store")
  void shouldCreateAnEventInTheEventStore() {
    // without the events of the scheduled command, which may land during the test (see TheCheckedCart)
    long eventsCount = waitForIt(() -> TheCheckedCart.eventsCount(applicationUnderTest), count -> count > 0);

    String mockCartId = random.uuid();
    String mockProductId = random.uuid();
    int mockQuantity = random.number(1);
    GraphQLResponse response = client.mutate(CHANGE_CART_ITEM,
        Map.of("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity));

    assertThat(response).isNotNull();
    assertThat(response.get("ChangeCartItem")).isEqualTo(true);

    // Verify number of events
    long expectedEventItemsCount = eventsCount + 1;
    waitForIt(() -> TheCheckedCart.eventsCount(applicationUnderTest),
        newEventsCount -> newEventsCount == expectedEventItemsCount);

    // Verify latest event
    List<Map<String, Object>> latestEvent = applicationUnderTest.query().events("Cart-" + mockCartId + "-event");
    assertThat(latestEvent).isNotNull();

    assertEventStoreKey(latestEvent.get(0), "Cart-" + mockCartId + "-event");
    assertThat(at(latestEvent, "[0].value.productId")).isEqualTo(mockProductId);
    assertThat(at(latestEvent, "[0].value.cartId")).isEqualTo(mockCartId);
    assertThat(at(latestEvent, "[0].value.quantity")).isEqualTo(mockQuantity);
    assertThat(at(latestEvent, "[0].kind")).isEqualTo("event");
    assertThat(at(latestEvent, "[0].entityTypeName")).isEqualTo("Cart");
    assertThat(at(latestEvent, "[0].typeName")).isEqualTo("CartItemChanged");
  }

  @Test
  @Order(3)
  @DisplayName("should create multiple events in the event store in batches")
  void shouldCreateMultipleEventsInTheEventStoreInBatches() {
    long eventsCount = waitForIt(() -> applicationUnderTest.count().events(), count -> count > 0);

    String mockCartId = random.uuid();
    int eventsToCreate = 30; // Using 30 because batches are of 25, making sure the batching gets triggered
    GraphQLResponse response = client.mutate(CHANGE_MULTIPLE_CART_ITEMS,
        Map.of("cartId", mockCartId, "itemsCount", eventsToCreate));

    assertThat(response).isNotNull();
    assertThat(response.get("ChangeMultipleCartItems")).isEqualTo(true);

    // Verify number of events
    long expectedEventItemsCount = eventsCount + eventsToCreate;
    waitForEventsCount(expectedEventItemsCount);

    // Verify latest events
    checkLatestEvents(mockCartId, eventsToCreate);
  }

  @Test
  @Order(4)
  @DisplayName("should create multiple events in the event store in multiple batches")
  void shouldCreateMultipleEventsInTheEventStoreInMultipleBatches() {
    long eventsCount = waitForIt(() -> applicationUnderTest.count().events(), count -> count > 0);

    String firstMockCartId = random.uuid();
    String secondMockCartId = random.uuid();
    int firstEventsToCreate = 101; // Using 100 because batches are of 101, making sure the batching gets triggered
    int secondEventsToCreate = 50; // Using 50 more to make sure the batching gets triggered per primary key
    GraphQLResponse response = client.mutate(CHANGE_MULTIPLE_CART_ITEMS_WITH_IDS, Map.of("items", List.of(
        object("cartId", firstMockCartId, "itemsCount", firstEventsToCreate),
        object("cartId", secondMockCartId, "itemsCount", secondEventsToCreate))));

    assertThat(response).isNotNull();
    assertThat(response.get("ChangeMultipleCartItemsWithIds")).isEqualTo(true);

    // Verify number of events
    long expectedEventItemsCount = eventsCount + firstEventsToCreate + secondEventsToCreate;
    waitForEventsCount(expectedEventItemsCount);

    checkLatestEvents(firstMockCartId, firstEventsToCreate);
    checkLatestEvents(secondMockCartId, secondEventsToCreate);
  }

  private void waitForEventsCount(long expectedEventItemsCount) {
    waitForIt(() -> applicationUnderTest.count().events(), newEventsCount -> {
      System.out.println("Current count " + newEventsCount + " \nExpected " + expectedEventItemsCount);
      return newEventsCount >= expectedEventItemsCount;
    });
  }

  private void checkLatestEvents(String cartId, int eventsToCreate) {
    String primaryKey = "Cart-" + cartId + "-event";
    List<Map<String, Object>> events = applicationUnderTest.query().events(primaryKey);
    assertThat(events).isNotNull();
    List<Integer> eventProductIds = new ArrayList<>();
    for (Map<String, Object> event : events) {
      assertEventStoreKey(event, primaryKey);
      int productIdNumber = Integer.parseInt((String) at(event, "value.productId"));
      assertThat(productIdNumber).isGreaterThanOrEqualTo(0);
      assertThat(productIdNumber).isLessThan(eventsToCreate);
      assertThat(at(event, "value.cartId")).isEqualTo(cartId);
      assertThat(at(event, "value.quantity")).isEqualTo(1);
      assertThat(event.get("kind")).isEqualTo("event");
      assertThat(event.get("entityTypeName")).isEqualTo("Cart");
      assertThat(event.get("typeName")).isEqualTo("CartItemChanged");
      eventProductIds.add(productIdNumber);
    }

    // Newest first: the events were registered with the product numbers 0, 1, 2, ... (see the class comment)
    assertThat(eventProductIds).hasSize(eventsToCreate);
    for (int i = 1; i < eventProductIds.size(); i++) {
      assertThat(eventProductIds.get(i - 1)).as("product numbers in %s", eventProductIds)
          .isGreaterThan(eventProductIds.get(i));
    }
  }
}
