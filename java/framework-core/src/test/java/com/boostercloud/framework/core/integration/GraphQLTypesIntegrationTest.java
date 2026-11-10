package com.boostercloud.framework.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The GraphQL shapes of commands and queries: nested input objects, object and union results, and subscriptions
 * whose stored document uses variables.
 */
class GraphQLTypesIntegrationTest extends IntegrationTestSupport {

  @Test
  void aCommandTakesNestedInputsAndReturnsAnObject() {
    Map<String, Object> result = graphQL("""
        mutation ($items: [CartItemInput!]!) {
          ChangeCartItems(input: {cartId: "cart-1", items: $items}) { cartId lines items { productId quantity } }
        }""", map("items", List.of(map("productId", "a", "quantity", 1), map("productId", "b", "quantity", 2))), null);

    assertThat(data(result)).isEqualTo(Map.of("ChangeCartItems", map("cartId", "cart-1", "lines", 2.0,
        "items", List.of(map("productId", "a", "quantity", 1.0), map("productId", "b", "quantity", 2.0)))));
    assertThat(data(graphQL("{ CartReadModel(id: \"cart-1\") { totalQuantity checks } }")))
        .isEqualTo(Map.of("CartReadModel", map("totalQuantity", 3.0, "checks", 2.0)));
  }

  @Test
  void aQueryReturnsAListOfAUnion() {
    graphQL("mutation { ChangeCartItem(input: {cartId: \"shared-id\", productId: \"p\", quantity: 4}) }");

    Map<String, Object> result = graphQL("""
        { FindStoreItem(input: {id: "shared-id"}) {
            __typename
            ... on CartView { cartId units }
            ... on ProductView { productId sku }
        } }""");

    assertThat(data(result)).isEqualTo(Map.of("FindStoreItem",
        List.of(map("__typename", "CartView", "cartId", "shared-id", "units", 4.0))));
    Map<String, Object> union = mapAt(data(graphQL("{ __type(name: \"StoreItem\") { kind possibleTypes { name } } }")),
        "__type");
    assertThat(union).isEqualTo(map("kind", "UNION",
        "possibleTypes", List.of(Map.of("name", "CartView"), Map.of("name", "ProductView"))));
  }

  @Test
  void aSubscriptionWithVariablesIsRunAgainWithThem() {
    socketConnect("ws");
    socketSend("ws", map("type", "connection_init", "payload", map()));
    socketSend("ws", map("type", "start", "id", "v", "payload", map(
        "query", "subscription ($cart: ID!) { CartReadModel(id: $cart) { id totalQuantity } }",
        "variables", map("cart", "cart-5"))));

    graphQL("mutation { ChangeCartItem(input: {cartId: \"cart-4\", productId: \"p\", quantity: 1}) }");
    graphQL("mutation { ChangeCartItem(input: {cartId: \"cart-5\", productId: \"p\", quantity: 6}) }");

    assertThat(socketMessages("ws")).filteredOn(message -> "data".equals(message.get("type")))
        .extracting(message -> at(message, "payload", "data", "CartReadModel"))
        .containsExactly(map("id", "cart-5", "totalQuantity", 6), map("id", "cart-5", "totalQuantity", 6));
  }

  @Test
  void theSchemaExposesTheApplication() {
    Map<String, Object> mutation = mapAt(data(graphQL("{ __schema { mutationType { fields { name } } } }")),
        "__schema", "mutationType");
    Map<String, Object> query = mapAt(data(graphQL(
        "{ __schema { queryType { fields(includeDeprecated: true) { name isDeprecated deprecationReason } } } }")),
        "__schema", "queryType");

    assertThat(listAt(mutation, "fields")).extracting(field -> field.get("name")).containsExactlyInAnyOrder(
        "AbandonCart", "ChangeCartItem", "ChangeCartItems", "CreateProduct", "DeleteProduct", "FailingCommand",
        "ImportProducts", "ReviewProduct", "RunDataMigrations", "TouchCart", "UpdateProduct");
    assertThat(listAt(query, "fields")).extracting(field -> field.get("name")).contains(
        "CartReadModel", "CartReadModels", "ListCartReadModels", "ProductReadModel", "ListProductReadModels",
        "CategoryReadModel", "ProductReviewReadModel", "eventsByEntity", "eventsByType", "CartTotalQuantity",
        "ProductsInCategory", "FindStoreItem");
    assertThat(listAt(query, "fields")).filteredOn(field -> Boolean.TRUE.equals(field.get("isDeprecated")))
        .extracting(field -> field.get("name") + ": " + field.get("deprecationReason"))
        .contains("CartReadModels: Method is deprecated. Use List* methods");
  }
}
