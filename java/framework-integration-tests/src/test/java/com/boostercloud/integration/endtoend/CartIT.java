package com.boostercloud.integration.endtoend;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.harness.Faker.commerce;
import static com.boostercloud.integration.harness.Faker.finance;
import static com.boostercloud.integration.harness.Faker.internet;
import static com.boostercloud.integration.harness.Faker.lorem;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLClientException;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.entities.ProductType;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/end-to-end/cart.integration.ts}: commands (before hooks included),
 * read models of one and several cart items, the reduction of a deleted product and a read model projecting two
 * entities. As in TypeScript the suites share one {@code client} variable, which the {@code Entities} block switches
 * to a {@code UserWithEmail} client for the rest of the file.
 */
@EndToEnd
@DisplayName("Cart end-to-end tests")
class CartIT {

  static final String CHANGE_CART_ITEM = """
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
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
        }
      }
      """;

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;

  CartIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    client = applicationUnderTest.graphql().client();
  }

  private static Map<String, Object> variables(Object... keysAndValues) {
    Map<String, Object> variables = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      variables.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return variables;
  }

  private static Map<String, Object> cartItem(String productId, int quantity) {
    return variables("__typename", "CartItem", "productId", productId, "quantity", quantity);
  }

  @Nested
  @Order(1)
  @DisplayName("Commands")
  class Commands {

    @Test
    @Order(1)
    @DisplayName("accepts a command successfully")
    void acceptsACommandSuccessfully() {
      GraphQLResponse response = client.mutate(CHANGE_CART_ITEM,
          variables("cartId", random.uuid(), "productId", random.uuid(), "quantity", random.number(1)));

      assertThat(response).isNotNull();
      assertThat(response.get("ChangeCartItem")).isEqualTo(true);
    }

    @Test
    @Order(2)
    @DisplayName("changes input before calling handle")
    void changesInputBeforeCallingHandle() {
      GraphQLResponse response = client.mutate(CHANGE_CART_ITEM, variables(
          "cartId", Constants.BEFORE_HOOK_MUTATION_ID, "productId", random.uuid(), "quantity", random.number(1)));

      assertThat(response).isNotNull();
      assertThat(response.get("ChangeCartItem")).isEqualTo(true);

      GraphQLResponse queryResult = waitForIt(
          () -> client.query(CART_READ_MODEL, variables("cartId", Constants.BEFORE_HOOK_MUTATION_ID + "-modified")),
          result -> result != null && result.get("CartReadModel") != null);

      assertThat(queryResult.get("CartReadModel.id")).isEqualTo(Constants.BEFORE_HOOK_MUTATION_ID_MODIFIED);
      assertThat(queryResult.get("CartReadModel.cartItems[0].quantity")).isEqualTo(Constants.BEFORE_HOOK_QUANTITY);
    }

    @Test
    @Order(3)
    @DisplayName("throws an exception when before hook throws")
    void throwsAnExceptionWhenBeforeHookThrows() {
      GraphQLClientException e = catchThrowableOfType(GraphQLClientException.class,
          () -> client.mutate(CHANGE_CART_ITEM, variables(
              "cartId", Constants.THROW_EXCEPTION_ID, "productId", random.uuid(), "quantity", random.number(1))));

      // TypeScript only asserts inside its catch block (the test passes when nothing is thrown); the port requires it
      assertThat(e).as("the mutation must fail").isNotNull();
      assertThat(e.graphQLErrors().get(0).message()).isEqualTo(Constants.BEFORE_HOOK_EXCEPTION + "-onError");
      assertThat(e.graphQLErrors().get(0).path()).containsExactly("ChangeCartItem");
    }

    @Nested
    @Order(4)
    @DisplayName("Query read models")
    class QueryReadModels {

      @Nested
      @Order(1)
      @DisplayName("1 cart item")
      class OneCartItem {
        private final String mockCartId = random.uuid();
        private final String mockProductId = random.uuid();
        private final int mockQuantity = random.number(1);

        @BeforeEach
        void provisionCart() {
          client.mutate(CHANGE_CART_ITEM,
              variables("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity));
        }

        @Test
        @DisplayName("should retrieve expected cart")
        void shouldRetrieveExpectedCart() {
          GraphQLResponse queryResult = waitForIt(
              () -> client.query(CART_READ_MODEL, variables("cartId", mockCartId)),
              result -> result != null && result.get("CartReadModel") != null);

          assertThat(queryResult.get("CartReadModel.id")).isEqualTo(mockCartId);
          assertThat((List<?>) queryResult.get("CartReadModel.cartItems")).hasSize(1);
          assertThat(queryResult.get("CartReadModel.cartItems[0]")).isEqualTo(cartItem(mockProductId, mockQuantity));
        }
      }

      @Nested
      @Order(2)
      @DisplayName("several cart items")
      class SeveralCartItems {
        private String mockCartId;
        private int mockCartItemsCount;
        private final List<Map<String, Object>> mockCartItems = new ArrayList<>();

        @BeforeEach
        void provisionCartItems() {
          mockCartId = random.uuid();
          mockCartItemsCount = random.number(2, 5);
          List<Callable<GraphQLResponse>> changeCartPromises = new ArrayList<>();

          for (int i = 0; i < mockCartItemsCount; i++) {
            String mockProductId = random.uuid();
            int mockQuantity = random.number(1);
            mockCartItems.add(variables("productId", mockProductId, "quantity", mockQuantity));

            String cartId = mockCartId;
            changeCartPromises.add(() -> client.mutate(CHANGE_CART_ITEM,
                variables("cartId", cartId, "productId", mockProductId, "quantity", mockQuantity)));
          }

          Promises.runAll(changeCartPromises);
        }

        @Test
        @DisplayName("should retrieve expected cart")
        void shouldRetrieveExpectedCart() {
          GraphQLResponse queryResult = waitForIt(
              () -> client.query(CART_READ_MODEL, variables("cartId", mockCartId)),
              result -> result != null && result.get("CartReadModel.cartItems") instanceof List<?> items
                  && items.size() == mockCartItemsCount);

          assertThat(queryResult.get("CartReadModel.id")).isEqualTo(mockCartId);
          List<?> cartItems = (List<?>) queryResult.get("CartReadModel.cartItems");
          assertThat(cartItems).hasSize(mockCartItemsCount);

          for (Map<String, Object> mockCartItem : mockCartItems) {
            boolean hasCartItem = cartItems.stream()
                .map(item -> (Map<?, ?>) item)
                .anyMatch(item -> mockCartItem.get("productId").equals(item.get("productId"))
                    && mockCartItem.get("quantity").equals(item.get("quantity")));

            assertThat(hasCartItem).as("cart item %s", mockCartItem).isTrue();
          }
        }
      }
    }
  }

  @Nested
  @Order(2)
  @DisplayName("Entities")
  class Entities {
    private String userEmail;
    private String authToken;

    @BeforeAll
    void before() {
      userEmail = internet.email();
      authToken = applicationUnderTest.token().forUser(userEmail, "UserWithEmail");
      client = applicationUnderTest.graphql().client(authToken);
    }

    @Nested
    @DisplayName("Reducers")
    class Reducers {
      private String mockSku;
      private String mockDisplayName;
      private String mockDescription;
      private int mockPriceInCents;
      private String mockCurrency;
      private Map<String, Object> mockProductDetails;
      private String mockProductType;

      private String productId;

      @BeforeEach
      void createProduct() {
        mockSku = random.uuid();
        mockDisplayName = commerce.productName();
        mockDescription = lorem.paragraph();
        mockPriceInCents = random.number(1);
        mockCurrency = finance.currencyCode();
        mockProductDetails = variables("color", commerce.color(), "size", commerce.productAdjective());
        mockProductType = ProductType.FURNITURE;

        // Add one item
        client.mutate("""
            mutation CreateProduct(
              $sku: String!
              $displayName: String!
              $description: String!
              $priceInCents: Float!
              $currency: String!
              $productDetails: JSON
              $productType: JSON
            ) {
              CreateProduct(
                input: {
                  sku: $sku
                  displayName: $displayName
                  description: $description
                  priceInCents: $priceInCents
                  currency: $currency
                  productDetails: $productDetails
                  productType: $productType
                }
              )
            }
            """, variables("sku", mockSku, "displayName", mockDisplayName, "description", mockDescription,
            "priceInCents", mockPriceInCents, "currency", mockCurrency, "productDetails", mockProductDetails,
            "productType", mockProductType));

        // Check that new product is available in read model
        GraphQLResponse products = waitForIt(
            () -> client.query("""
                query {
                  ProductReadModels {
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
                    productDetails
                    productType
                  }
                }
                """),
            result -> result != null && result.get("ProductReadModels") instanceof List<?> list
                && list.stream().anyMatch(product -> mockSku.equals(((Map<?, ?>) product).get("sku"))));

        Map<?, ?> product = ((List<?>) products.get("ProductReadModels")).stream()
            .map(item -> (Map<?, ?>) item)
            .filter(item -> mockSku.equals(item.get("sku")))
            .findFirst()
            .orElseThrow();
        productId = (String) product.get("id");

        Map<String, Object> expectedResult = variables(
            "__typename", "ProductReadModel",
            "id", productId,
            "sku", mockSku,
            "displayName", mockDisplayName,
            "description", mockDescription,
            "price", variables("__typename", "Money", "cents", mockPriceInCents, "currency", mockCurrency),
            "availability", 0,
            "deleted", false,
            "productDetails", mockProductDetails,
            "productType", mockProductType);

        assertThat(product).isEqualTo(expectedResult);
      }

      @Test
      @DisplayName("should reduce the entity as expected")
      void shouldReduceTheEntityAsExpected() {
        String adminEmail = internet.email();
        String adminAuthToken = applicationUnderTest.token().forUser(adminEmail, "Admin");
        client = applicationUnderTest.graphql().client(adminAuthToken);
        // Delete a product given an id
        client.mutate("""
            mutation DeleteProduct($productId: ID!) {
              DeleteProduct(input: { productId: $productId })
            }
            """, variables("productId", productId));

        client = applicationUnderTest.graphql().client(authToken);
        // Retrieve updated entity
        GraphQLResponse queryResult = waitForIt(
            () -> client.query("""
                query ProductReadModel($productId: ID!) {
                  ProductReadModel(id: $productId) {
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
                """, variables("productId", productId)),
            result -> result == null || result.get("ProductReadModel") == null);

        Object productData = queryResult.get("ProductReadModel");
        assertThat(productData).isNull();
        assertThat(queryResult.data()).containsKey("ProductReadModel");
      }
    }
  }

  @Nested
  @Order(3)
  @DisplayName("Read models")
  class ReadModels {

    @Nested
    @DisplayName("projecting two entities")
    class ProjectingTwoEntities {
      private final String mockCartId = random.uuid();
      private final String mockPaymentId = random.uuid();
      private final String mockProductId = random.uuid();
      private final int mockQuantity = random.number(1);
      private final String mockConfirmationToken = random.alphaNumeric(10);

      @BeforeEach
      void provisionCart() {
        client.mutate(CHANGE_CART_ITEM,
            variables("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity));
      }

      @Test
      @DisplayName("should project changes for both entities")
      void shouldProjectChangesForBothEntities() {
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(CART_READ_MODEL, variables("cartId", mockCartId)),
            result -> result != null && result.get("CartReadModel") != null);

        Map<String, Object> expectedResult = variables(
            "__typename", "CartReadModel",
            "id", mockCartId,
            "cartItems", List.of(cartItem(mockProductId, mockQuantity)));

        assertThat(queryResult.get("CartReadModel")).isEqualTo(expectedResult);

        // Make payment
        client.mutate("""
            mutation ConfirmPayment($paymentId: ID!, $cartId: ID!, $confirmationToken: String!) {
              ConfirmPayment(input: { paymentId: $paymentId, cartId: $cartId, confirmationToken: $confirmationToken })
            }
            """, variables("paymentId", mockPaymentId, "cartId", mockCartId,
            "confirmationToken", mockConfirmationToken));

        // Retrieve updated read model
        GraphQLResponse updatedQueryResult = waitForIt(
            () -> client.query("""
                query CartReadModel($cartId: ID!) {
                  CartReadModel(id: $cartId) {
                    id
                    cartItems {
                      productId
                      quantity
                    }
                    payment {
                      confirmationToken
                      id
                      cartId
                    }
                  }
                }
                """, variables("cartId", mockCartId)),
            result -> result != null && result.get("CartReadModel.payment") != null);

        Map<String, Object> expectedUpdatedResult = variables(
            "__typename", "CartReadModel",
            "id", mockCartId,
            "cartItems", List.of(cartItem(mockProductId, mockQuantity)),
            "payment", variables(
                "__typename", "Payment",
                "confirmationToken", mockConfirmationToken,
                "id", mockPaymentId,
                "cartId", mockCartId));

        assertThat(updatedQueryResult.get("CartReadModel")).isEqualTo(expectedUpdatedResult);
      }
    }
  }
}
