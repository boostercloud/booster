package com.boostercloud.integration.providerspecific.local;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-specific/local/commands.integration.ts}: a {@code ChangeCartItem} sent to the
 * local application, the event it stores in {@code .booster/events.json} and the snapshot of the cart.
 *
 * <p>No TypeScript npm script runs this file ({@code integration/local-start} and {@code local-stop} only run the
 * {@code start} and {@code stop} directories) and every {@code it} is an {@code xit} without a reason, so every test is
 * {@link Disabled} here too. The suite uses the shared application, whose sandbox
 * ({@code local-project-integration-sandbox}) is the one {@code provider-specific/local/setup.ts} starts. The NeDB
 * reads of {@code events.json} ({@code loadDatabase} with {@code getAllData}, {@code findOne} and {@code count}) are
 * the local test helper's raw queries over the same file ({@code query().events("Cart-<id>-event")} and
 * {@code "Cart-<id>-snapshot"}).
 *
 * <p>Enabled, every test fails, here as in TypeScript: the document of {@code utils.ts} declares
 * {@code $quantity: Float}, but {@code ChangeCartItemInput.quantity} is {@code Float!} (the command's
 * {@code quantity} is required), so GraphQL validation (graphql-js and graphql-java alike) rejects it with
 * {@code Variable 'quantity' of type 'Float' used in position expecting type 'Float!'}. The port keeps the TypeScript
 * document. Beyond that, {@code should store event in the database} sends no token, so the stored event would have no
 * {@code currentUser} {@code test@test.com}.
 */
@EndToEnd
@DisplayName("commands")
class LocalCommandsIT {

  /** {@code changeCartItem} of {@code integration/provider-specific/local/utils.ts}. */
  static final String CHANGE_CART_ITEM = """
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }
      """;

  /** The reason of every {@code xit} of {@code provider-specific/local} (TypeScript gives none). */
  static final String SKIPPED_IN_TYPESCRIPT = "Skipped in TypeScript (xit, no reason given)";

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;

  LocalCommandsIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    client = applicationUnderTest.graphql().client();
  }

  /** {@code changeCartItem(client, cartId, productId, quantity)} of {@code utils.ts}. */
  static GraphQLResponse changeCartItem(GraphQLClient client, String cartId, String productId, int quantity) {
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("cartId", cartId);
    variables.put("productId", productId);
    variables.put("quantity", quantity);
    return client.mutate(CHANGE_CART_ITEM, variables);
  }

  @Nested
  @Order(1)
  @DisplayName("valid command")
  class ValidCommand {
    private String mockCartId;
    private String mockProductId;
    private int mockQuantity;

    @BeforeEach
    void beforeEach() {
      mockCartId = random.uuid();
      mockProductId = random.uuid();
      mockQuantity = random.number(1);
    }

    @Test
    @Order(1)
    @Disabled(SKIPPED_IN_TYPESCRIPT)
    @DisplayName("should successfully process a command")
    void shouldSuccessfullyProcessACommand() {
      GraphQLResponse mutationResult = changeCartItem(client, mockCartId, mockProductId, mockQuantity);

      assertThat(mutationResult).isNotNull();
      assertThat(mutationResult.get("ChangeCartItem")).isEqualTo(true);
    }

    @Test
    @Order(2)
    @Disabled(SKIPPED_IN_TYPESCRIPT)
    @DisplayName("should store event in the database")
    void shouldStoreEventInTheDatabase() {
      changeCartItem(client, mockCartId, mockProductId, mockQuantity);

      // Wait until event is stored in database
      List<Map<String, Object>> events = waitForIt(
          () -> applicationUnderTest.query().events("Cart-" + mockCartId + "-event"),
          stored -> !stored.isEmpty());

      // Verify the event content (chai's deep.include: every expected key with a deep-equal value)
      Map<String, Object> result = events.get(0);
      Map<String, Object> expectedValue = new LinkedHashMap<>();
      expectedValue.put("cartId", mockCartId);
      expectedValue.put("productId", mockProductId);
      expectedValue.put("quantity", mockQuantity);
      assertThat(result)
          .containsEntry("version", 1)
          .containsEntry("kind", "event")
          .containsEntry("superKind", "domain")
          .containsEntry("entityID", mockCartId)
          .containsEntry("currentUser", Map.of("username", "test@test.com", "role", ""))
          .containsEntry("entityTypeName", "Cart")
          .containsEntry("typeName", "CartItemChanged")
          .containsEntry("value", expectedValue);
    }

    @Test
    @Order(3)
    @Disabled(SKIPPED_IN_TYPESCRIPT)
    @DisplayName("should create a snapshot after 5 events")
    void shouldCreateASnapshotAfter5Events() {
      int quantity;
      long expectedSnapshotQuantity = 0;

      for (int i = 0; i < 5; i++) {
        quantity = random.number();
        expectedSnapshotQuantity += quantity;
        changeCartItem(client, mockCartId, mockProductId, quantity);
      }

      // Sixth event - Quantity shouldn't be added to snapshot
      quantity = random.number();
      changeCartItem(client, mockCartId, mockProductId, quantity);

      long expected = expectedSnapshotQuantity;
      List<Map<String, Object>> snapshots = waitForIt(
          () -> applicationUnderTest.query().events("Cart-" + mockCartId + "-snapshot"),
          stored -> stored.stream().anyMatch(record -> firstItemIs(record, mockProductId, expected)));

      assertThat(snapshots).hasSizeGreaterThanOrEqualTo(1);
    }
  }

  /** {@code record.value?.cartItems[0]?.productId === productId && record.value?.cartItems[0]?.quantity === n}. */
  private static boolean firstItemIs(Map<String, Object> record, String productId, long quantity) {
    if (!(record.get("value") instanceof Map<?, ?> value) || !(value.get("cartItems") instanceof List<?> items)
        || items.isEmpty() || !(items.get(0) instanceof Map<?, ?> item)) {
      return false;
    }
    return productId.equals(item.get("productId")) && item.get("quantity") instanceof Number number
        && number.doubleValue() == quantity;
  }
}
