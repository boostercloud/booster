package com.boostercloud.integration.providerspecific.local.durability;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The GraphQL documents of the durability and recovery suites. They use the TypeScript application's operations
 * (the same names, arguments and fields as {@code src/commands} and {@code src/read-models}).
 */
final class Documents {

  static final String INCREMENT_COUNTER = """
      mutation IncrementCounter($counterId: ID!, $identifier: String!) {
        IncrementCounter(input: { counterId: $counterId, identifier: $identifier })
      }
      """;

  static final String COUNTER_READ_MODEL = """
      query CounterReadModel($id: ID!) {
        CounterReadModel(id: $id) {
          id
          identifier
          amount
        }
      }
      """;

  static final String COUNTER_SUBSCRIPTION = """
      subscription CounterReadModel($id: ID!) {
        CounterReadModel(id: $id) {
          id
          identifier
          amount
        }
      }
      """;

  static final String CHANGE_CART_ITEM = """
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }
      """;

  static final String CHANGE_MULTIPLE_CART_ITEMS = """
      mutation ChangeMultipleCartItems($cartId: ID!, $itemsCount: Float!) {
        ChangeMultipleCartItems(input: { cartId: $cartId, itemsCount: $itemsCount })
      }
      """;

  static final String CART_READ_MODEL = """
      query CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
          cartItems {
            productId
            quantity
          }
          checks
          cartItemsIds
          cartItemsSize
        }
      }
      """;

  static final String CHECKED_CART = """
      query {
        CartReadModel(id: "the-checked-cart") {
          id
          checks
        }
      }
      """;

  static final String LIST_CART_READ_MODELS = """
      query ListCartReadModels($filter: ListCartReadModelFilter) {
        ListCartReadModels(filter: $filter) {
          items {
            id
            cartItems {
              productId
              quantity
            }
            checks
          }
          count
        }
      }
      """;

  static final String CREATE_PRODUCT = """
      mutation CreateProduct($sku: String!, $productID: ID, $priceInCents: Float!) {
        CreateProduct(
          input: {
            sku: $sku
            productID: $productID
            priceInCents: $priceInCents
            displayName: "durable product"
            description: "survives restarts"
            currency: "EUR"
          }
        )
      }
      """;

  static final String PRODUCT_READ_MODEL = """
      query ProductReadModel($id: ID!) {
        ProductReadModel(id: $id) {
          id
          sku
          displayName
          description
          price {
            cents
            currency
          }
          availability
          deleted
        }
      }
      """;

  static final String PRODUCTS_BY_SKU = """
      query ProductsBySKU($sku: ID!) {
        ProductsBySKU(id: $sku) {
          id
          products
        }
      }
      """;

  static final String MIGRATE_COMMAND = """
      mutation MigrateCommand {
        MigrateCommand
      }
      """;

  static final String LIST_DATA_MIGRATIONS = """
      query ListDataMigrationsReadModels {
        ListDataMigrationsReadModels(filter: {}) {
          items {
            id
            status
          }
          count
        }
      }
      """;

  static final String COUNTER_EVENTS = """
      query CounterEvents($counterId: ID!) {
        eventsByEntity(entity: Counter, entityID: $counterId) {
          createdAt
          entity
          entityID
          requestID
          type
          user {
            id
            roles
            username
          }
          value
        }
      }
      """;

  static final String CART_EVENTS = """
      query CartEvents($cartId: ID!) {
        eventsByEntity(entity: Cart, entityID: $cartId) {
          createdAt
          entity
          entityID
          type
          user {
            id
            roles
            username
          }
          value
        }
      }
      """;

  private Documents() {}

  /**
   * @param keysAndValues alternating names and values
   * @return the variables, in order
   */
  static Map<String, Object> variables(Object... keysAndValues) {
    Map<String, Object> variables = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      variables.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return variables;
  }
}
