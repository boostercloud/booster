package com.boostercloud.framework.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.integration.app.AppState;
import com.boostercloud.framework.core.integration.app.common.CartItem;
import com.boostercloud.framework.core.integration.app.entities.Cart;
import com.boostercloud.framework.core.integration.app.readmodels.CartReadModel;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.GraphQLResponse;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The write path end to end: a GraphQL mutation runs the command (before hook, handler), its events are stored and
 * dispatched, the event handlers register more events, the entity is reduced into a snapshot and projected into its
 * read model, which GraphQL then returns by id with its calculated field.
 */
class CommandToReadModelIntegrationTest extends IntegrationTestSupport {

  private static final String CHANGE_CART_ITEM = """
      mutation ($cartId: ID!, $productId: ID!, $quantity: Float!) {
        ChangeCartItem(input: {cartId: $cartId, productId: $productId, quantity: $quantity})
      }""";

  private static final String CART_BY_ID = """
      query ($id: ID!) {
        CartReadModel(id: $id) { id checks totalQuantity items { productId quantity } }
      }""";

  private Map<String, Object> changeCartItem(String cartId, String productId, int quantity) {
    return graphQL(CHANGE_CART_ITEM, map("cartId", cartId, "productId", productId, "quantity", quantity), null);
  }

  @Test
  void aMutationIsProjectedIntoTheReadModel() {
    Map<String, Object> result = changeCartItem("cart-1", "product-1", 2);

    assertThat(data(result)).isEqualTo(Map.of("ChangeCartItem", true));
    Map<String, Object> cart = data(graphQL(CART_BY_ID, map("id", "cart-1"), null));
    assertThat(cart).isEqualTo(Map.of("CartReadModel", map(
        "id", "cart-1",
        "checks", 1.0,
        "totalQuantity", 2.0,
        "items", List.of(map("productId", "product-1", "quantity", 2.0)))));
  }

  @Test
  void storesTheEventsTheirSnapshotsAndTheReadModelDocument() {
    changeCartItem("cart-1", "product-1", 2);
    changeCartItem("cart-1", "product-2", 1);

    List<EventEnvelope> events = provider.events().events();
    assertThat(events).extracting(EventEnvelope::typeName)
        .containsExactly("CartItemChanged", "CartChecked", "CartItemChanged", "CartChecked");
    assertThat(events).allSatisfy(event -> {
      assertThat(event.entityTypeName()).isEqualTo("Cart");
      assertThat(event.entityID()).isEqualTo(UUID.of("cart-1"));
      assertThat(event.version()).isEqualTo(1);
    });
    assertThat(events.get(1).requestID()).isEqualTo(events.get(0).requestID());

    EntitySnapshotEnvelope latest = provider.events().latestEntitySnapshot(config, "Cart", UUID.of("cart-1"))
        .orElseThrow();
    assertThat(latest.snapshottedEventCreatedAt()).isEqualTo(events.getLast().createdAt());
    assertThat(latest.value()).isEqualTo(Map.of("id", "cart-1", "checks", 2,
        "items", List.of(Map.of("productId", "product-1", "quantity", 2),
            Map.of("productId", "product-2", "quantity", 1))));

    List<Map<String, Object>> documents = provider.readModels().documents("CartReadModel");
    assertThat(documents).singleElement().satisfies(document -> {
      assertThat(document).containsEntry("id", "cart-1").containsEntry("internalNote", "projected 2");
      Map<String, Object> metadata = mapAt(document, "boosterMetadata");
      assertThat(metadata).containsEntry("schemaVersion", 1);
      assertThat(metadata.get("version")).isEqualTo(4);
      assertThat(at(document, "boosterMetadata", "lastProjectionInfo"))
          .isEqualTo(Map.of("entityId", "cart-1", "entityName", "Cart", "entityUpdatedAt", latest.createdAt(),
              "projectionMethod", "CartReadModel.project"));
    });
  }

  @Test
  void theEntityAndTheReadModelCanBeReadFromCode() {
    changeCartItem("cart-1", "product-1", 2);
    changeCartItem("cart-1", "product-1", 0);
    changeCartItem("cart-1", "product-3", 5);

    assertThat(Booster.entity(Cart.class, UUID.of("cart-1")))
        .contains(new Cart(UUID.of("cart-1"), List.of(new CartItem(UUID.of("product-3"), 5)), 3));
    assertThat(Booster.entity(Cart.class, UUID.of("nobody"))).isEmpty();
    assertThat(Booster.readModel(CartReadModel.class).filter(Map.of("id", Map.of("eq", UUID.of("cart-1")))).search())
        .singleElement().satisfies(cart -> assertThat(cart.totalQuantity()).isEqualTo(5));
  }

  @Test
  void theBeforeHookOfTheCommandValidatesAndRewritesTheInput() {
    Map<String, Object> rejected = changeCartItem("cart-1", "product-1", -1);
    assertThat(errorMessage(rejected)).isEqualTo("The quantity cannot be negative");
    assertThat(errors(rejected).getFirst()).containsEntry("extensions", Map.of("code", "InvalidParameterError"))
        .containsEntry("path", List.of("ChangeCartItem"));
    assertThat(provider.events().events()).isEmpty();

    changeCartItem("cart-1", "product-1", 500);
    assertThat(at(data(graphQL(CART_BY_ID, map("id", "cart-1"), null)), "CartReadModel", "totalQuantity"))
        .isEqualTo(99.0);
  }

  @Test
  void eventHandlersAndTheGlobalEventHandlerSeeEveryEvent() {
    changeCartItem("cart-1", "product-1", 2);

    assertThat(AppState.EVENTS).containsExactlyInAnyOrder("CartItemChanged", "CartChecked");
  }

  @Test
  void aFailingEventHandlerGoesToTheGlobalErrorHandlerAndDoesNotStopTheProjection() {
    changeCartItem("cart-1", "product-1", 13);

    assertThat(AppState.EVENT_HANDLER_ERRORS).containsExactly("Unlucky quantity for cart cart-1");
    assertThat(AppState.ERRORS).containsExactly("Unlucky quantity for cart cart-1");
    assertThat(at(data(graphQL(CART_BY_ID, map("id", "cart-1"), null)), "CartReadModel"))
        .isEqualTo(map("id", "cart-1", "checks", 0.0, "totalQuantity", 13.0,
            "items", List.of(map("productId", "product-1", "quantity", 13.0))));
    assertThat(RecordingLogger.INSTANCE.errors()).isNotEmpty();
  }

  @Test
  void aMissingReadModelIsNull() {
    assertThat(data(graphQL(CART_BY_ID, map("id", "unknown"), null))).isEqualTo(map("CartReadModel", null));
  }

  @Test
  void theResponseCarriesTheDefaultHeaders() {
    config.defaultResponseHeaders.put("X-Store", "integration");

    GraphQLResponse response = post("{ CartReadModel(id: \"x\") { id } }", null, null);

    assertThat(response.headers()).containsEntry("X-Store", "integration");
  }
}
