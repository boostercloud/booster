package com.boostercloud.integration.endtoend;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.harness.Faker.commerce;
import static com.boostercloud.integration.harness.Faker.finance;
import static com.boostercloud.integration.harness.Faker.internet;
import static com.boostercloud.integration.harness.Faker.lorem;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.boostercloud.application.tester.TokenOptions;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLClientException;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import com.boostercloud.integration.helper.AppHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/end-to-end/read-models.integration.ts}: queries by id and lists of read
 * models (before hooks, every filter operator, implicit AND, sorting, limits, offset cursors, projections of fields
 * and calculated fields from GraphQL and from code, fragments), projections (two entities, array join keys, entity
 * and read-model join keys, a join-key function that skips) and the authorization of read models (public, roles and
 * a custom authorizer).
 *
 * <p>As in TypeScript, a module-level anonymous {@code client} is shared by the blocks and by the car-domain helpers
 * at the end of the file; {@code projecting two entities with array joinKey} uses a {@code UserWithEmail} client of
 * its own. The application is the shared one, so every block works on fresh random ids and the list assertions are
 * filtered by them.
 *
 * <p>Differences, documented where they happen: the assertions TypeScript only makes in a {@code catch} block are
 * strict here, and the {@code cursor.id === '100'} assertions of the searches without {@code limit}, which only
 * Azure's default page size produces, run on Azure; on the local provider (TypeScript and Java alike) the cursor of a
 * search without limit is {@code (limit ?? 1) + offset = '1'}, pinned by additional local tests.
 */
@EndToEnd
@DisplayName("Read models end-to-end tests")
class ReadModelsIT {

  // <editor-fold desc="GraphQL documents (verbatim TypeScript text)">

  private static final String CHANGE_CART_ITEM = """
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }
      """;

  private static final String CART_READ_MODEL = """
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

  private static final String CART_READ_MODEL_ID = """
      query CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
        }
      }
      """;

  private static final String CART_READ_MODEL_WITH_SHIPPING_ADDRESS = """
      query CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
          cartItems {
            productId
            quantity
          }
          shippingAddress {
            firstName
          }
        }
      }
      """;

  private static final String CART_READ_MODEL_ID_AND_SHIPPING_ADDRESS = """
      query CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
          shippingAddress {
            firstName
          }
        }
      }
      """;

  private static final String UPDATE_SHIPPING_ADDRESS = """
      mutation UpdateShippingAddress($cartId: ID!, $address: AddressInput!) {
        UpdateShippingAddress(input: { cartId: $cartId, address: $address })
      }
      """;

  private static final String CONFIRM_PAYMENT = """
      mutation ConfirmPayment($paymentId: ID!, $cartId: ID!, $confirmationToken: String!) {
        ConfirmPayment(input: { paymentId: $paymentId, cartId: $cartId, confirmationToken: $confirmationToken })
      }
      """;

  private static final String CART_READ_MODELS_WITH_SHIPPING_ADDRESS = """
      query CartReadModels($filter: CartReadModelFilter) {
        CartReadModels(filter: $filter) {
          id
          shippingAddress {
            firstName
          }
        }
      }
      """;

  private static final String CART_READ_MODELS_WITHOUT_FILTER = """
      query CartReadModels {
        CartReadModels {
          id
        }
      }
      """;

  private static final String CART_READ_MODELS = """
      query CartReadModels($filter: CartReadModelFilter) {
        CartReadModels(filter: $filter) {
          id
        }
      }
      """;

  private static final String LIST_CART_READ_MODELS_WITH_SHIPPING_ADDRESS = """
      query ListCartReadModels($filter: ListCartReadModelFilter) {
        ListCartReadModels(filter: $filter) {
          items {
            id
            shippingAddress {
              firstName
            }
          }
        }
      }
      """;

  private static final String LIST_CART_READ_MODELS_WITH_PAYMENT = """
      query ListCartReadModels($filter: ListCartReadModelFilter) {
        ListCartReadModels(filter: $filter) {
          items {
            id
            cartItems {
              productId
              quantity
            }
            shippingAddress {
              firstName
            }
            payment {
              id
              confirmationToken
            }
          }
        }
      }
      """;

  private static final String LIST_CART_READ_MODELS_WITHOUT_FILTER = """
      query ListCartReadModels {
        ListCartReadModels {
          items {
            id
          }
        }
      }
      """;

  private static final String LIST_CART_READ_MODELS = """
      query ListCartReadModels($filter: ListCartReadModelFilter) {
        ListCartReadModels(filter: $filter) {
          items {
            id
          }
        }
      }
      """;

  private static final String LIST_CART_READ_MODELS_WITH_EVERY_FIELD = """
      query ListCartReadModels($filter: ListCartReadModelFilter) {
        ListCartReadModels(filter: $filter) {
          items {
            id
            cartItems {
              productId
              quantity
            }
            checks
            shippingAddress {
              firstName
            }
            payment {
              cartId
            }
            cartItemsIds
          }
          cursor
        }
      }
      """;

  private static final String SORTED_LIST_CART_READ_MODELS = """
      query ListCartReadModels($filterBy: ListCartReadModelFilter, $sortBy: CartReadModelSortBy) {
        ListCartReadModels(filter: $filterBy, sortBy: $sortBy) {
          items {
            id
          }
        }
      }
      """;

  private static final String SORTED_LIST_CART_READ_MODELS_WITH_SHIPPING_ADDRESS = """
      query ListCartReadModels($filterBy: ListCartReadModelFilter, $sortBy: CartReadModelSortBy) {
        ListCartReadModels(filter: $filterBy, sortBy: $sortBy) {
          items {
            id
            shippingAddress {
              firstName
            }
          }
        }
      }
      """;

  private static final String LIMITED_LIST_CART_READ_MODELS = """
      query ListCartReadModels($limit: Int) {
        ListCartReadModels(limit: $limit) {
          items {
            id
          }
        }
      }
      """;

  private static final String PAGINATED_LIST_CART_READ_MODELS = """
      query ListCartReadModels($limit: Int, $afterCursor: JSON) {
        ListCartReadModels(limit: $limit, afterCursor: $afterCursor) {
          cursor
          items {
            id
          }
        }
      }
      """;

  private static final String PAGINATED_LIST_CART_READ_MODELS_WITH_COUNT = """
      query ListCartReadModels($limit: Int, $afterCursor: JSON) {
        ListCartReadModels(limit: $limit, afterCursor: $afterCursor) {
          cursor
          items {
            id
          }
          count
        }
      }
      """;

  private static final String CART_SHIPPING_ADDRESS = """
      mutation CartShippingAddress($cartId: ID!, $paginatedVersion: Boolean!) {
        CartShippingAddress(input: { cartId: $cartId, paginatedVersion: $paginatedVersion })
      }
      """;

  private static final String CART_READ_MODEL_WITH_FRAGMENT = """
      query CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
          cartItems {
            ...cartItemDetails
          }
        }
      }
      fragment cartItemDetails on CartItem {
        productId
        quantity
      }
      """;

  private static final String LIST_CART_READ_MODELS_WITH_FRAGMENT = """
      query ListCartReadModels($filterBy: ListCartReadModelFilter) {
        ListCartReadModels(filter: $filterBy) {
          items {
            ...cart
          }
          cursor
        }
      }
      fragment cart on CartReadModel {
        id
        cartItems {
          productId
          quantity
        }
      }
      """;

  private static final String CART_READ_MODELS_WITH_MY_ADDRESS = """
      query CartReadModels($filter: CartReadModelFilter) {
        CartReadModels(filter: $filter) {
          id
          myAddress {
            firstName
            lastName
            country
            state
            postalCode
            address
          }
        }
      }
      """;

  private static final String CART_MY_ADDRESS = """
      mutation CartMyAddress($cartId: ID!, $paginatedVersion: Boolean!) {
        CartMyAddress(input: { cartId: $cartId, paginatedVersion: $paginatedVersion })
      }
      """;

  private static final String CREATE_PRODUCT_WITH_ID = """
      mutation CreateProduct(
        $productID: ID!
        $sku: String!
        $displayName: String!
        $description: String!
        $priceInCents: Float!
        $currency: String!
      ) {
        CreateProduct(
          input: {
            productID: $productID
            sku: $sku
            displayName: $displayName
            description: $description
            priceInCents: $priceInCents
            currency: $currency
          }
        )
      }
      """;

  private static final String PRODUCT_READ_MODELS_WITH_PACK_IDS = """
      query ProductReadModels($products: [ID!]!) {
        ProductReadModels(filter: { id: { in: $products } }) {
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
          packs {
            id
          }
          productDetails
          productType
        }
      }
      """;

  private static final String PRODUCT_READ_MODELS_WITH_PACKS = """
      query ProductReadModels($products: [ID!]!) {
        ProductReadModels(filter: { id: { in: $products } }) {
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
          packs {
            id
            name
            products
          }
          productDetails
          productType
        }
      }
      """;

  private static final String CREATE_PACK = """
      mutation CreatePack($packID: ID!, $name: String!, $products: [ID!]!) {
        CreatePack(input: { packID: $packID, name: $name, products: $products })
      }
      """;

  private static final String PRODUCT_READ_MODEL_ID = """
      query ProductReadModel($id: ID!) {
        ProductReadModel(id: $id) {
          id
        }
      }
      """;

  private static final String SPECIAL_REPORTS_READ_MODEL_ID = """
      query SpecialReportsReadModel($id: ID!) {
        SpecialReportsReadModel(id: $id) {
          id
        }
      }
      """;

  private static final String ADD_CAR_MODEL = """
      mutation AddCarModel($id: ID!, $name: String!, $brand: String!) {
        AddCarModel(input: { id: $id, name: $name, brand: $brand })
      }
      """;

  private static final String ADD_CAR_OWNER = """
      mutation AddCarOwner($id: ID!, $name: String!) {
        AddCarOwner(input: { id: $id, name: $name })
      }
      """;

  private static final String ADD_PURCHASE_OFFER = """
      mutation AddPurchaseOffer($id: ID!, $name: String!, $purchaseIds: [String!]!) {
        AddPurchaseOffer(input: { id: $id, name: $name, purchaseIds: $purchaseIds })
      }
      """;

  private static final String BUY_CAR = """
      mutation BuyCar($id: ID!, $modelId: ID!, $ownerId: ID!) {
        BuyCar(input: { id: $id, modelId: $modelId, ownerId: $ownerId })
      }
      """;

  private static final String LIST_CAR_PURCHASES_READ_MODELS = """
      query ListCarPurchasesReadModels($filter: ListCarPurchasesReadModelFilter) {
        ListCarPurchasesReadModels(filter: $filter) {
          items {
            id
            carModel {
              id
              name
              brand
            }
            carOwner {
              id
              name
            }
          }
        }
      }
      """;

  private static final String UPDATE_CAR_MODEL_NAME = """
      mutation UpdateCarModelName($id: ID!, $name: String!) {
        UpdateCarModelName(input: { id: $id, name: $name })
      }
      """;

  private static final String UPDATE_CAR_OWNER_NAME = """
      mutation UpdateCarOwnerName($id: ID!, $name: String!) {
        UpdateCarOwnerName(input: { id: $id, name: $name })
      }
      """;

  // </editor-fold>

  private final ApplicationUnderTest applicationUnderTest;
  /** The module-level {@code let client} of the TypeScript file (anonymous). */
  private GraphQLClient client;

  ReadModelsIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    client = applicationUnderTest.graphql().client();
  }

  // <editor-fold desc="Small helpers">

  /** An insertion-ordered map ({@code { key: value, ... }} of the TypeScript sources). */
  private static Map<String, Object> map(Object... keysAndValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }

  /** {@code { field: { op: value } }}. */
  private static Map<String, Object> filter(String field, String operator, Object value) {
    return map(field, map(operator, value));
  }

  /** {@code { and: [...] }}. */
  private static Map<String, Object> and(Object... filters) {
    return map("and", List.of(filters));
  }

  /** {@code { or: [...] }}. */
  private static Map<String, Object> or(Object... filters) {
    return map("or", List.of(filters));
  }

  /** The list at {@code path}, or {@code null} when it is not a list (TypeScript's optional chaining). */
  private static List<?> listAt(GraphQLResponse response, String path) {
    return response != null && response.get(path) instanceof List<?> list ? list : null;
  }

  /** {@code response?.data?.<path>?.length}, {@code -1} when it is not a list. */
  private static int sizeAt(GraphQLResponse response, String path) {
    List<?> list = listAt(response, path);
    return list == null ? -1 : list.size();
  }

  /** The value at {@code path} of a raw value ({@link GraphQLResponse#get(String)} over any tree). */
  private static Object at(Object root, String path) {
    return root instanceof Map<?, ?> map ? GraphQLResponse.fromResult(Map.of("data", map)).get(path) : null;
  }

  /** {@code { __typename: 'CartItem', productId, quantity }}. */
  private static Map<String, Object> cartItem(String productId, int quantity) {
    return map("__typename", "CartItem", "productId", productId, "quantity", quantity);
  }

  /** {@code { firstName, lastName, country, state, postalCode, address }}, each {@code random.word()}. */
  private static Map<String, Object> randomAddress() {
    return map(
        "firstName", random.word(),
        "lastName", random.word(),
        "country", random.word(),
        "state", random.word(),
        "postalCode", random.word(),
        "address", random.word());
  }

  private static boolean isLocal() {
    return AppHelper.LOCAL.equals(AppHelper.testedProvider());
  }

  private static boolean isAzure() {
    return AppHelper.AZURE.equals(AppHelper.testedProvider());
  }

  private static boolean isAzureOrLocal() {
    return isAzure() || isLocal();
  }

  /** The TypeScript warning block of the provider-gated tests, then the JUnit assumption. */
  private static void onlyWhen(boolean condition, String warning) {
    if (!condition) {
      System.out.println("****************** Warning **********************");
      System.out.println(warning);
      System.out.println("*************************************************");
    }
    assumeTrue(condition, warning);
  }

  /** {@code a > b ? -1 : a < b ? 1 : 0}: JavaScript's descending string order (UTF-16 code units). */
  private static final Comparator<String> DESCENDING = Comparator.reverseOrder();

  // </editor-fold>

  @Nested
  @Order(1)
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
        // provisioning a cart
        client.mutate(CHANGE_CART_ITEM,
            map("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity));
      }

      @Test
      @Order(1)
      @DisplayName("should retrieve expected cart")
      void shouldRetrieveExpectedCart() {
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(CART_READ_MODEL, map("cartId", mockCartId)),
            result -> result != null && result.get("CartReadModel") != null);

        assertThat(queryResult.get("CartReadModel.id")).isEqualTo(mockCartId);
        assertThat(listAt(queryResult, "CartReadModel.cartItems")).hasSize(1);
        assertThat(queryResult.get("CartReadModel.cartItems[0]")).isEqualTo(cartItem(mockProductId, mockQuantity));
      }

      @Test
      @Order(2)
      @DisplayName("should apply modified filter by before hooks")
      void shouldApplyModifiedFilterByBeforeHooks() {
        // We create a cart with id 'before-fn-test-modified', but we query for
        // 'before-fn-test', which will then change the filter after two "before" functions
        // to return the original cart (id 'before-fn-test-modified')
        Map<String, Object> variables =
            map("cartId", "before-fn-test-modified", "productId", Constants.BEFORE_HOOK_PRODUCT_ID, "quantity", 1);
        client.mutate(CHANGE_CART_ITEM, variables);
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(CART_READ_MODEL, map("cartId", "before-fn-test")),
            result -> result != null && result.get("CartReadModel") != null);

        assertThat(queryResult.get("CartReadModel.id")).isEqualTo(variables.get("cartId"));
      }

      @Test
      @Order(3)
      @DisplayName("should return exceptions thrown by before functions")
      void shouldReturnExceptionsThrownByBeforeFunctions() {
        GraphQLClientException e = catchThrowableOfType(GraphQLClientException.class,
            () -> waitForIt(
                () -> client.query(CART_READ_MODEL, map("cartId", Constants.THROW_EXCEPTION_ID)),
                result -> true));

        // TypeScript only asserts inside its catch block (the test passes when nothing is thrown); the port requires it
        assertThat(e).as("the query must fail").isNotNull();
        assertThat(e.graphQLErrors().get(0).message()).isEqualTo(Constants.BEFORE_HOOK_EXCEPTION);
        assertThat(e.graphQLErrors().get(0).path()).containsExactly("CartReadModel");
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
          mockCartItems.add(map("productId", mockProductId, "quantity", mockQuantity));

          String cartId = mockCartId;
          changeCartPromises.add(() -> client.mutate(CHANGE_CART_ITEM,
              map("cartId", cartId, "productId", mockProductId, "quantity", mockQuantity)));
        }

        Promises.runAll(changeCartPromises);
      }

      @Test
      @DisplayName("should retrieve expected cart")
      void shouldRetrieveExpectedCart() {
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(CART_READ_MODEL, map("cartId", mockCartId)),
            result -> sizeAt(result, "CartReadModel.cartItems") == mockCartItemsCount);

        assertThat(queryResult.get("CartReadModel.id")).isEqualTo(mockCartId);
        List<?> cartItems = listAt(queryResult, "CartReadModel.cartItems");
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

    @Nested
    @Order(3)
    @DisplayName("query lists of carts")
    class QueryListsOfCarts {
      private String mockCartId;
      private final List<Map<String, Object>> mockCartItems = new ArrayList<>();
      private String mockProductId;
      private int mockQuantity;
      private final String mockConfirmationToken = random.alphaNumeric(10);
      private Map<String, Object> mockAddress;

      @BeforeEach
      void provisionCartWithShippingAddress() {
        mockCartId = random.uuid();

        mockProductId = random.uuid();
        mockQuantity = 2;
        mockCartItems.add(map("productId", mockProductId, "quantity", mockQuantity));

        client.mutate(CHANGE_CART_ITEM,
            map("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity));

        waitForIt(
            () -> client.query(CART_READ_MODEL, map("cartId", mockCartId)),
            result -> result != null && mockCartId.equals(result.get("CartReadModel.id")));

        mockAddress = randomAddress();

        client.mutate(UPDATE_SHIPPING_ADDRESS, map("cartId", mockCartId, "address", mockAddress));

        Map<String, Object> filter = map("shippingAddress", filter("firstName", "eq", firstName()));
        waitForIt(
            () -> client.query(CART_READ_MODELS_WITH_SHIPPING_ADDRESS, map("filter", filter)),
            result -> sizeAt(result, "CartReadModels") >= 1
                && firstName().equals(result.get("CartReadModels[0].shippingAddress.firstName")));
      }

      private String firstName() {
        return (String) mockAddress.get("firstName");
      }

      /** {@code carts?.length >= 1 && carts[0].shippingAddress?.firstName === mockAddress.firstName}. */
      private Predicate<GraphQLResponse> firstCartHasTheFirstName() {
        return result -> sizeAt(result, "ListCartReadModels.items") >= 1
            && firstName().equals(result.get("ListCartReadModels.items[0].shippingAddress.firstName"));
      }

      /** The assertions of the {@code isDefined}, {@code regex} and {@code iRegex} tests. */
      private void assertOnlyTheMockCartWithItsFirstName(GraphQLResponse queryResult) {
        List<?> cartData = listAt(queryResult, "ListCartReadModels.items");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData).hasSize(1);
        assertThat(at(cartData.getFirst(), "id")).isEqualTo(mockCartId);
        assertThat(at(cartData.getFirst(), "shippingAddress.firstName")).isEqualTo(firstName());
      }

      @Test
      @Order(1)
      @DisplayName("should retrieve a list of carts using deprecated methods")
      void shouldRetrieveAListOfCartsUsingDeprecatedMethods() {
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(CART_READ_MODELS_WITHOUT_FILTER),
            result -> sizeAt(result, "CartReadModels") >= 1);

        List<?> cartData = listAt(queryResult, "CartReadModels");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData.size()).isGreaterThanOrEqualTo(1);
      }

      @Test
      @Order(2)
      @DisplayName("should retrieve a specific cart using filters using deprecated methods")
      void shouldRetrieveASpecificCartUsingFiltersUsingDeprecatedMethods() {
        Map<String, Object> filter = filter("id", "eq", mockCartId);
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(CART_READ_MODELS, map("filter", filter)),
            result -> sizeAt(result, "CartReadModels") >= 1);

        List<?> cartData = listAt(queryResult, "CartReadModels");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData).hasSize(1);
        assertThat(at(cartData.getFirst(), "id")).isEqualTo(mockCartId);
      }

      @Test
      @Order(3)
      @DisplayName("should retrieve a list of carts using nested filters")
      void shouldRetrieveAListOfCartsUsingNestedFilters() {
        Map<String, Object> filter = and(
            filter("id", "eq", mockCartId),
            map("shippingAddress", filter("firstName", "eq", firstName())));
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(CART_READ_MODELS, map("filter", filter)),
            result -> sizeAt(result, "CartReadModels") >= 1 && mockCartId.equals(result.get("CartReadModels[0].id")));

        List<?> cartData = listAt(queryResult, "CartReadModels");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData).hasSize(1);
        assertThat(at(cartData.getFirst(), "id")).isEqualTo(mockCartId);
      }

      @Test
      @Order(4)
      @DisplayName("should retrieve a list of carts using  complex filters and deprecated methods")
      void shouldRetrieveAListOfCartsUsingComplexFiltersAndDeprecatedMethods() {
        Map<String, Object> filter = filter("cartItems", "includes", map("productId", mockProductId, "quantity", 2));
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(CART_READ_MODELS, map("filter", filter)),
            result -> sizeAt(result, "CartReadModels") >= 1);

        List<?> cartData = listAt(queryResult, "CartReadModels");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData).hasSize(1);
        assertThat(at(cartData.getFirst(), "id")).isEqualTo(mockCartId);
      }

      @Test
      @Order(5)
      @DisplayName("should retrieve a list of carts when filter by isDefined true")
      void shouldRetrieveAListOfCartsWhenFilterByIsDefinedTrue() {
        Map<String, Object> filter = and(
            filter("id", "eq", mockCartId),
            map("shippingAddress", filter("firstName", "isDefined", true)));
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS_WITH_SHIPPING_ADDRESS, map("filter", filter)),
            firstCartHasTheFirstName()::test);

        assertOnlyTheMockCartWithItsFirstName(queryResult);
      }

      @Test
      @Order(6)
      @DisplayName("should retrieve a list of carts when filter by isDefined false")
      void shouldRetrieveAListOfCartsWhenFilterByIsDefinedFalse() {
        Map<String, Object> filter = and(
            filter("id", "eq", mockCartId),
            map("payment", filter("id", "isDefined", false)));
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS_WITH_SHIPPING_ADDRESS, map("filter", filter)),
            firstCartHasTheFirstName()::test);

        assertOnlyTheMockCartWithItsFirstName(queryResult);
      }

      @Test
      @Order(7)
      @DisplayName("should retrieve a list of carts when filter by isDefined true with AND")
      void shouldRetrieveAListOfCartsWhenFilterByIsDefinedTrueWithAnd() {
        Map<String, Object> filter = and(
            filter("id", "eq", mockCartId),
            and(
                map("shippingAddress", filter("firstName", "isDefined", true)),
                map("shippingAddress", filter("firstName", "eq", firstName()))));
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS_WITH_SHIPPING_ADDRESS, map("filter", filter)),
            firstCartHasTheFirstName()::test);

        assertOnlyTheMockCartWithItsFirstName(queryResult);
      }

      @Test
      @Order(8)
      @DisplayName("should retrieve a list of carts when filter by isDefined true with OR")
      void shouldRetrieveAListOfCartsWhenFilterByIsDefinedTrueWithOr() {
        Map<String, Object> filter = and(
            filter("id", "eq", mockCartId),
            or(
                map("shippingAddress", filter("lastName", "isDefined", false)),
                map("shippingAddress", filter("firstName", "eq", firstName()))));
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS_WITH_SHIPPING_ADDRESS, map("filter", filter)),
            firstCartHasTheFirstName()::test);

        assertOnlyTheMockCartWithItsFirstName(queryResult);
      }

      @Test
      @Order(9)
      @DisplayName("should retrieve a list of carts when filter by isDefined for Objects")
      void shouldRetrieveAListOfCartsWhenFilterByIsDefinedForObjects() {
        Map<String, Object> filter = and(
            filter("id", "eq", mockCartId),
            filter("payment", "isDefined", false));

        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS_WITH_SHIPPING_ADDRESS, map("filter", filter)),
            firstCartHasTheFirstName()::test);

        assertOnlyTheMockCartWithItsFirstName(queryResult);
      }

      @Test
      @Order(10)
      @DisplayName("should retrieve a list of carts when filter by isDefined with complex queries")
      void shouldRetrieveAListOfCartsWhenFilterByIsDefinedWithComplexQueries() {
        String mockPaymentId = random.uuid();
        client.mutate(CONFIRM_PAYMENT,
            map("paymentId", mockPaymentId, "cartId", mockCartId, "confirmationToken", mockConfirmationToken));

        Map<String, Object> filter = and(
            filter("id", "eq", mockCartId),
            map("shippingAddress", filter("firstName", "eq", firstName())),
            or(
                filter("cartItems", "isDefined", false),
                filter("cartItems", "includes", map("productId", mockProductId, "quantity", 2))),
            map("payment", filter("confirmationToken", "eq", mockConfirmationToken)),
            map("payment", filter("id", "ne", null)));

        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS_WITH_PAYMENT, map("filter", filter)),
            result -> sizeAt(result, "ListCartReadModels.items") >= 1
                && result.get("ListCartReadModels.items[0].payment.id") != null);

        List<?> cartData = listAt(queryResult, "ListCartReadModels.items");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData).hasSize(1);
        assertThat(at(cartData.getFirst(), "id")).isEqualTo(mockCartId);
        assertThat(at(cartData.getFirst(), "shippingAddress.firstName")).isEqualTo(firstName());
        assertThat(at(cartData.getFirst(), "cartItems[0].productId")).isEqualTo(mockProductId);
        assertThat(at(cartData.getFirst(), "payment.id")).isEqualTo(mockPaymentId);
      }

      @Test
      @Order(11)
      @DisplayName("should retrieve a list of carts when filter by contains with UUID fields")
      void shouldRetrieveAListOfCartsWhenFilterByContainsWithUuidFields() {
        String partialMockCartId = mockCartId.substring(1, mockCartId.length() - 1);
        Map<String, Object> filter = and(
            filter("id", "eq", mockCartId),
            filter("id", "contains", partialMockCartId));

        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS_WITH_SHIPPING_ADDRESS, map("filter", filter)),
            result -> sizeAt(result, "ListCartReadModels.items") >= 1
                && mockCartId.equals(result.get("ListCartReadModels.items[0].id")));

        List<?> cartData = listAt(queryResult, "ListCartReadModels.items");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData).hasSize(1);
        assertThat(at(cartData.getFirst(), "id")).isEqualTo(mockCartId);
      }

      @Test
      @Order(12)
      @DisplayName("should retrieve a list of carts when filter by regex")
      void shouldRetrieveAListOfCartsWhenFilterByRegex() {
        onlyWhen(isAzureOrLocal(), "Only Azure and Local provider implement the regex filter. Skipping");
        Map<String, Object> filter = and(
            filter("id", "eq", mockCartId),
            map("shippingAddress", filter("firstName", "regex", "^" + firstName().charAt(0) + ".*")));
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS_WITH_SHIPPING_ADDRESS, map("filter", filter)),
            firstCartHasTheFirstName()::test);

        assertOnlyTheMockCartWithItsFirstName(queryResult);
      }

      @Test
      @Order(13)
      @DisplayName("should retrieve a list of carts when filter by iRegex")
      void shouldRetrieveAListOfCartsWhenFilterByIRegex() {
        onlyWhen(isAzureOrLocal(), "Only Azure and Local provider implement the iRegex filter. Skipping");
        String firstLetter = firstName().substring(0, 1);
        String inverseFirstLetter =
            firstLetter.equals(firstLetter.toUpperCase()) ? firstLetter.toLowerCase() : firstLetter.toUpperCase();
        Map<String, Object> filter = and(
            filter("id", "eq", mockCartId),
            map("shippingAddress", filter("firstName", "iRegex", "^" + inverseFirstLetter + ".*")));
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS_WITH_SHIPPING_ADDRESS, map("filter", filter)),
            firstCartHasTheFirstName()::test);

        assertOnlyTheMockCartWithItsFirstName(queryResult);
      }

      @Test
      @Order(14)
      @DisplayName("should retrieve a list of carts when filter by null")
      void shouldRetrieveAListOfCartsWhenFilterByNull() {
        String mockPaymentId = random.uuid();
        client.mutate(CONFIRM_PAYMENT,
            map("paymentId", mockPaymentId, "cartId", mockCartId, "confirmationToken", mockConfirmationToken));

        Map<String, Object> filter = and(
            filter("id", "eq", mockCartId),
            map("payment", filter("confirmationToken", "ne", null)),
            map("payment", filter("id", "ne", null)));

        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS_WITH_PAYMENT, map("filter", filter)),
            result -> sizeAt(result, "ListCartReadModels.items") >= 1
                && mockCartId.equals(result.get("ListCartReadModels.items[0].id"))
                && result.get("ListCartReadModels.items[0].payment.id") != null);

        List<?> cartData = listAt(queryResult, "ListCartReadModels.items");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData).hasSize(1);
        assertThat(at(cartData.getFirst(), "id")).isEqualTo(mockCartId);
        assertThat(at(cartData.getFirst(), "shippingAddress.firstName")).isEqualTo(firstName());
        assertThat(at(cartData.getFirst(), "cartItems[0].productId")).isEqualTo(mockProductId);
        assertThat(at(cartData.getFirst(), "payment.id")).isEqualTo(mockPaymentId);
      }

      @Test
      @Order(15)
      @DisplayName("should retrieve a list of carts using paginated read model")
      void shouldRetrieveAListOfCartsUsingPaginatedReadModel() {
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS_WITHOUT_FILTER),
            result -> sizeAt(result, "ListCartReadModels.items") >= 1);

        List<?> cartData = listAt(queryResult, "ListCartReadModels.items");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData.size()).isGreaterThanOrEqualTo(1);
      }

      @Test
      @Order(16)
      @DisplayName("should retrieve a specific cart using filters using paginated read model")
      void shouldRetrieveASpecificCartUsingFiltersUsingPaginatedReadModel() {
        Map<String, Object> filter = filter("id", "eq", mockCartId);
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS, map("filter", filter)),
            result -> sizeAt(result, "ListCartReadModels.items") >= 1);

        List<?> cartData = listAt(queryResult, "ListCartReadModels.items");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData).hasSize(1);
        assertThat(at(cartData.getFirst(), "id")).isEqualTo(mockCartId);
      }

      @Test
      @Order(17)
      @DisplayName("should retrieve a list of carts using complex filters using paginated read model")
      void shouldRetrieveAListOfCartsUsingComplexFiltersUsingPaginatedReadModel() {
        Map<String, Object> filter = filter("cartItems", "includes", map("productId", mockProductId, "quantity", 2));
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS, map("filter", filter)),
            result -> sizeAt(result, "ListCartReadModels.items") >= 1);

        List<?> cartData = listAt(queryResult, "ListCartReadModels.items");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData).hasSize(1);
        assertThat(at(cartData.getFirst(), "id")).isEqualTo(mockCartId);
      }

      @Test
      @Order(18)
      @DisplayName("should retrieve a list of carts using AND by default with paginated read model")
      void shouldRetrieveAListOfCartsUsingAndByDefaultWithPaginatedReadModel() {
        Map<String, Object> filter = map(
            "shippingAddress", filter("firstName", "eq", firstName()),
            "cartItems", map("includes", map("productId", mockProductId, "quantity", mockQuantity)),
            "cartItemsIds", map("includes", mockProductId));
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS_WITH_EVERY_FIELD, map("filter", filter)),
            result -> sizeAt(result, "ListCartReadModels.items") == 1);

        List<?> cartData = listAt(queryResult, "ListCartReadModels.items");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData).hasSize(1);
        assertThat(at(cartData.getFirst(), "id")).isEqualTo(mockCartId);
      }

      @Test
      @Order(19)
      @DisplayName("should not fail if search a list of carts with empty results")
      void shouldNotFailIfSearchAListOfCartsWithEmptyResults() {
        Map<String, Object> filter = filter("cartItems", "includes", map("productId", mockProductId, "quantity", 200));
        GraphQLResponse queryResult = client.query(LIST_CART_READ_MODELS, map("filter", filter));

        List<?> cartData = listAt(queryResult, "ListCartReadModels.items");

        assertThat(cartData).isEmpty();
      }
    }

    @Nested
    @Order(4)
    @DisplayName("query sorted lists of carts")
    class QuerySortedListsOfCarts {
      private final List<Map<String, Object>> mockCartItems = new ArrayList<>();
      private static final int CART_ITEMS = 5;
      private Map<String, Object> mockAddress;

      @BeforeEach
      void provisionCarts() {
        for (int i = 0; i < CART_ITEMS; i++) {
          int mockQuantity = i;
          String mockProductId = random.uuid();
          String mockCartId = random.uuid();
          String mockFirstName = String.valueOf((char) (i + 65));
          mockAddress = randomAddress();
          mockAddress.put("firstName", mockFirstName);
          mockCartItems.add(map(
              "id", mockCartId, "productId", mockProductId, "quantity", mockQuantity, "firstName", mockFirstName));

          client.mutate(CHANGE_CART_ITEM,
              map("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity));

          waitForIt(
              () -> client.query(CART_READ_MODEL, map("cartId", mockCartId)),
              result -> result != null && result.get("CartReadModel") != null);

          client.mutate(UPDATE_SHIPPING_ADDRESS, map("cartId", mockCartId, "address", mockAddress));

          waitForIt(
              () -> client.query(CART_READ_MODEL_WITH_SHIPPING_ADDRESS, map("cartId", mockCartId)),
              result -> result != null && result.get("CartReadModel.shippingAddress.firstName") != null);
        }
      }

      @AfterEach
      void clearCartItems() {
        mockCartItems.clear();
      }

      private List<String> expectedIds() {
        return mockCartItems.stream().map(item -> (String) item.get("id")).toList();
      }

      private GraphQLResponse sortedCarts(String document, Map<String, Object> sortBy) {
        List<String> expectedIds = expectedIds();
        return waitForIt(
            () -> client.query(document, map("filterBy", filter("id", "in", expectedIds), "sortBy", sortBy)),
            result -> sizeAt(result, "ListCartReadModels.items") == CART_ITEMS);
      }

      @Test
      @Order(1)
      @DisplayName("should retrieve a sorted list of carts using paginated read model")
      void shouldRetrieveASortedListOfCartsUsingPaginatedReadModel() {
        onlyWhen(isAzureOrLocal(), "Only Azure and Local provider implement the sort option");

        List<String> expectedIds = expectedIds();
        Map<String, Object> mockedSortBy = map("id", "DESC");
        GraphQLResponse queryResult = sortedCarts(SORTED_LIST_CART_READ_MODELS, mockedSortBy);

        List<?> cartData = listAt(queryResult, "ListCartReadModels.items");

        assertThat(cartData).isInstanceOf(List.class);
        List<String> reverseExpectedIds = expectedIds.stream().sorted(DESCENDING).toList();
        assertThat(cartData.stream().map(item -> at(item, "id")).toList()).isEqualTo(reverseExpectedIds);
      }

      @Test
      @Order(2)
      @DisplayName("should retrieve a sorted list of carts using nested fields")
      void shouldRetrieveASortedListOfCartsUsingNestedFields() {
        onlyWhen(isAzureOrLocal(), "Only Azure and Local provider implement the sort option");

        Map<String, Object> mockedSortBy = map("shippingAddress", map("firstName", "DESC"));
        GraphQLResponse queryResult = sortedCarts(SORTED_LIST_CART_READ_MODELS_WITH_SHIPPING_ADDRESS, mockedSortBy);

        List<?> cartData = listAt(queryResult, "ListCartReadModels.items");

        assertThat(cartData).isInstanceOf(List.class);
        List<String> expectedFirstNames = mockCartItems.stream().map(item -> (String) item.get("firstName")).toList();
        List<String> reverseExpectedFirstNames = expectedFirstNames.stream().sorted(DESCENDING).toList();
        List<Object> names = cartData.stream().map(item -> at(item, "shippingAddress.firstName")).toList();
        assertThat(names).isEqualTo(reverseExpectedFirstNames);
      }

      @Test
      @Order(3)
      @DisplayName("should retrieve a sorted list of carts using two fields")
      void shouldRetrieveASortedListOfCartsUsingTwoFields() {
        onlyWhen(isLocal(), "Only Local provider implement the sort option for more than one sort field");

        List<String> expectedIds = expectedIds();
        Map<String, Object> mockedSortBy = map("id", "DESC", "shippingAddress", map("firstName", "ASC"));
        GraphQLResponse queryResult = sortedCarts(SORTED_LIST_CART_READ_MODELS, mockedSortBy);

        List<?> cartData = listAt(queryResult, "ListCartReadModels.items");

        assertThat(cartData).isInstanceOf(List.class);
        List<String> reverseExpectedIds = expectedIds.stream().sorted(DESCENDING).toList();
        assertThat(cartData.stream().map(item -> at(item, "id")).toList()).isEqualTo(reverseExpectedIds);
      }
    }

    @Nested
    @Order(5)
    @DisplayName("query using pagination")
    class QueryUsingPagination {
      private final List<String> mockCartIds = new ArrayList<>();
      private final List<Map<String, Object>> mockCartItems = new ArrayList<>();
      private String mockProductId;
      private int mockQuantity;
      private static final int CARTS_NUMBER = 3;

      @BeforeEach
      void provisionCarts() {
        mockProductId = random.uuid();
        mockQuantity = 2;
        mockCartItems.add(map("productId", mockProductId, "quantity", mockQuantity));

        // TypeScript keeps the promises of earlier beforeEach runs in a context-level array and awaits them again
        // (they are already settled, so nothing happens): only the new mutations run here
        List<Callable<GraphQLResponse>> changeCartPromises = new ArrayList<>();
        for (int i = 0; i < CARTS_NUMBER; i++) {
          String mockCartId = random.uuid();
          mockCartIds.add(mockCartId);

          String mockProductId = random.uuid();
          int mockQuantity = random.number(1);
          mockCartItems.add(map("productId", mockProductId, "quantity", mockQuantity));

          changeCartPromises.add(() -> client.mutate(CHANGE_CART_ITEM,
              map("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity)));
        }

        Promises.runAll(changeCartPromises);
      }

      @Test
      @Order(1)
      @DisplayName("should retrieve a list of carts limited to 2 items")
      void shouldRetrieveAListOfCartsLimitedTo2Items() {
        int limit = 2;
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(LIMITED_LIST_CART_READ_MODELS, map("limit", limit)),
            result -> sizeAt(result, "ListCartReadModels.items") == 2);

        List<?> cartData = listAt(queryResult, "ListCartReadModels.items");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData).hasSize(2);
      }

      @Test
      @Order(2)
      @DisplayName("should retrieve a list of carts paginated")
      void shouldRetrieveAListOfCartsPaginated() {
        int limit = 1;
        Object cursor = null;

        for (int i = 0; i < CARTS_NUMBER; i++) {
          Map<String, Object> variables = map("limit", limit);
          if (cursor != null) {
            // Apollo drops undefined variables: the first page sends no afterCursor at all
            variables.put("afterCursor", cursor);
          }
          GraphQLResponse queryResult = waitForIt(
              () -> client.query(PAGINATED_LIST_CART_READ_MODELS, variables),
              result -> sizeAt(result, "ListCartReadModels.items") == 1);

          List<?> currentPageCartData = listAt(queryResult, "ListCartReadModels.items");

          cursor = queryResult.get("ListCartReadModels.cursor");

          if (cursor != null) {
            if (isAzureOrLocal()) {
              assertThat(at(cursor, "id")).isEqualTo(String.valueOf(i + 1));
            } else {
              assertThat(at(cursor, "id")).isEqualTo(at(currentPageCartData.getFirst(), "id"));
            }
          }
          assertThat(currentPageCartData).isInstanceOf(List.class);
          assertThat(currentPageCartData).hasSize(1);
          assertThat(cursor).isNotNull();
        }
      }

      @Test
      @Order(3)
      @DisplayName("should handle legacy numeric cursors correctly (Azure/Local)")
      void shouldHandleLegacyNumericCursorsCorrectly() {
        if (!isAzureOrLocal()) {
          System.out.println("Legacy cursor test only applies to Azure/Local provider. Skipping.");
        }
        assumeTrue(isAzureOrLocal(), "Legacy cursor test only applies to Azure/Local provider. Skipping.");

        int limit = 1;
        // We know we have cartsNumber (3) items, so we can manually test specific legacy cursor positions

        // Test starting from position 1 (should skip first item and get the second)
        Map<String, Object> legacyCursor = map("id", "1");

        GraphQLResponse queryResult = waitForIt(
            () -> client.query(PAGINATED_LIST_CART_READ_MODELS_WITH_COUNT,
                map("limit", limit, "afterCursor", legacyCursor)),
            result -> sizeAt(result, "ListCartReadModels.items") == 1);

        Object result = queryResult.get("ListCartReadModels");

        // Verify we got exactly one item (the second cart)
        assertThat(at(result, "items")).isInstanceOf(List.class);
        assertThat((List<?>) at(result, "items")).hasSize(1);
        assertThat(at(result, "count")).isEqualTo(1);

        // Verify the returned cursor is correctly calculated using our fixed logic
        if (at(result, "cursor") != null) {
          assertThat(at(result, "cursor.id")).isInstanceOf(String.class);
          assertThat((String) at(result, "cursor.id")).isNotEmpty();
          // Should be '2' (offset 1 + limit 1) based page-based offset logic: offset + effectiveLimit
          assertThat(at(result, "cursor.id")).isEqualTo("2");
          // Legacy cursors don't have continuation tokens
          assertThat(((Map<?, ?>) at(result, "cursor")).containsKey("continuationToken")).isFalse();
        }
      }
    }

    @Nested
    @Order(6)
    @DisplayName("projecting fields")
    class ProjectingFields {
      private final String mockCartId = random.uuid();
      private final String mockProductId = random.uuid();
      private final int mockQuantity = random.number(1);
      private final Map<String, Object> mockAddress = randomAddress();

      @BeforeEach
      void provisionCartWithShippingAddress() {
        // TypeScript skips the whole context on AWS, which is not ported: the gate only documents it
        assumeFalse("AWS".equals(AppHelper.testedProvider()), "AWS Provider ReadModel projecting field is not supported");

        // provisioning a cart
        client.mutate(CHANGE_CART_ITEM,
            map("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity));

        waitForIt(
            () -> client.query(CART_READ_MODEL_ID, map("cartId", mockCartId)),
            result -> result != null && result.get("CartReadModel") != null);

        // update shipping address
        client.mutate(UPDATE_SHIPPING_ADDRESS, map("cartId", mockCartId, "address", mockAddress));

        waitForIt(
            () -> client.query(CART_READ_MODEL_ID_AND_SHIPPING_ADDRESS, map("cartId", mockCartId)),
            result -> result != null && result.get("CartReadModel") != null
                && mockAddress.get("firstName").equals(result.get("CartReadModel.shippingAddress.firstName")));
      }

      /** {@code [{ id: mockCartId, shippingAddress: { ...the six address fields } }]}, without {@code __typename}. */
      private List<Map<String, Object>> expectedItems() {
        return List.of(map(
            "id", mockCartId,
            "shippingAddress", map(
                "firstName", mockAddress.get("firstName"),
                "lastName", mockAddress.get("lastName"),
                "country", mockAddress.get("country"),
                "state", mockAddress.get("state"),
                "postalCode", mockAddress.get("postalCode"),
                "address", mockAddress.get("address"))));
      }

      private Object cartShippingAddress(boolean paginatedVersion) {
        GraphQLResponse queryResult = waitForIt(
            () -> client.mutate(CART_SHIPPING_ADDRESS,
                map("cartId", mockCartId, "paginatedVersion", paginatedVersion)),
            result -> result != null && result.get("CartShippingAddress") != null);

        return queryResult.get("CartShippingAddress");
      }

      @Test
      @Order(1)
      @DisplayName("with paginatedVersion true")
      void withPaginatedVersionTrue() {
        Object cartShippingAddress = cartShippingAddress(true);

        assertThat(at(cartShippingAddress, "items")).isEqualTo(expectedItems());
        assertThat(at(cartShippingAddress, "count")).isEqualTo(1);
        // TypeScript expects '100' on Azure and Local: only Azure's default page size gives it (see the class Javadoc)
        if (isAzure()) {
          assertThat(at(cartShippingAddress, "cursor.id")).isEqualTo("100");
        }
      }

      @Test
      @Order(2)
      @DisplayName("with paginatedVersion false")
      void withPaginatedVersionFalse() {
        Object cartShippingAddress = cartShippingAddress(false);

        assertThat(cartShippingAddress).isEqualTo(expectedItems());
      }

      @Test
      @Order(3)
      @DisplayName("with paginatedVersion true, the local cursor of a search without limit is '1'")
      void withPaginatedVersionTrueTheLocalCursorOfASearchWithoutLimitIs1() {
        assumeTrue(isLocal(), "local provider behaviour: cursor = (limit ?? 1) + offset");

        Object cartShippingAddress = cartShippingAddress(true);

        assertThat(at(cartShippingAddress, "items")).isEqualTo(expectedItems());
        assertThat(at(cartShippingAddress, "count")).isEqualTo(1);
        assertThat(at(cartShippingAddress, "cursor")).isEqualTo(map("id", "1"));
      }
    }

    @Nested
    @Order(7)
    @DisplayName("query with fragments")
    class QueryWithFragments {
      private final String mockCartId = random.uuid();
      private final String mockProductId = random.uuid();
      private final int mockQuantity = random.number(1);

      @BeforeEach
      void provisionCart() {
        // provisioning a cart
        client.mutate(CHANGE_CART_ITEM,
            map("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity));
      }

      @Test
      @Order(1)
      @DisplayName("should retrieve expected cart")
      void shouldRetrieveExpectedCart() {
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(CART_READ_MODEL_WITH_FRAGMENT, map("cartId", mockCartId)),
            result -> result != null && result.get("CartReadModel") != null);

        assertThat(queryResult.get("CartReadModel.id")).isEqualTo(mockCartId);
        assertThat(listAt(queryResult, "CartReadModel.cartItems")).hasSize(1);
        assertThat(queryResult.get("CartReadModel.cartItems[0]")).isEqualTo(cartItem(mockProductId, mockQuantity));
      }

      private GraphQLResponse cartsWithFragment() {
        return waitForIt(
            () -> client.query(LIST_CART_READ_MODELS_WITH_FRAGMENT, map("filterBy", filter("id", "eq", mockCartId))),
            result -> sizeAt(result, "ListCartReadModels.items") == 1);
      }

      @Test
      @Order(2)
      @DisplayName("should retrieve list of items")
      void shouldRetrieveListOfItems() {
        int limit = 1;
        Object cursor;

        for (int i = 0; i < limit; i++) {
          GraphQLResponse queryResult = cartsWithFragment();

          List<?> currentPageCartData = listAt(queryResult, "ListCartReadModels.items");

          cursor = queryResult.get("ListCartReadModels.cursor");

          if (cursor != null) {
            // TypeScript expects '100' on Azure and Local: only Azure's default page size gives it (class Javadoc)
            if (isAzure()) {
              assertThat(at(cursor, "id")).isEqualTo("100");
            } else if (!isLocal()) {
              assertThat(at(cursor, "id")).isEqualTo(at(currentPageCartData.getFirst(), "id"));
            }
          }
          assertThat(currentPageCartData).isInstanceOf(List.class);
          assertThat(currentPageCartData).hasSize(1);
          assertThat(cursor).isNotNull();
          assertThat(at(currentPageCartData.getFirst(), "id")).isEqualTo(mockCartId);
          assertThat((List<?>) at(currentPageCartData.getFirst(), "cartItems")).hasSize(1);
          assertThat(at(currentPageCartData.getFirst(), "cartItems[0].productId")).isEqualTo(mockProductId);
        }
      }

      @Test
      @Order(3)
      @DisplayName("should retrieve list of items, with the local cursor of a search without limit")
      void shouldRetrieveListOfItemsWithTheLocalCursorOfASearchWithoutLimit() {
        assumeTrue(isLocal(), "local provider behaviour: cursor = (limit ?? 1) + offset");

        GraphQLResponse queryResult = cartsWithFragment();

        // cursor is a JSON scalar: no __typename
        assertThat(queryResult.get("ListCartReadModels.cursor")).isEqualTo(map("id", "1"));
        assertThat(queryResult.get("ListCartReadModels.items[0].id")).isEqualTo(mockCartId);
      }
    }

    @Nested
    @Order(8)
    @DisplayName("projecting calculated fields")
    class ProjectingCalculatedFields {
      private final String mockCartId = random.uuid();
      private final String mockProductId = random.uuid();
      private final int mockQuantity = random.number(1);
      private final Map<String, Object> mockAddress = randomAddress();

      @BeforeEach
      void provisionCartWithShippingAddress() {
        // provisioning a cart
        client.mutate(CHANGE_CART_ITEM,
            map("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity));

        waitForIt(
            () -> client.query(CART_READ_MODEL_ID, map("cartId", mockCartId)),
            result -> result != null && result.get("CartReadModel") != null);

        // update shipping address
        client.mutate(UPDATE_SHIPPING_ADDRESS, map("cartId", mockCartId, "address", mockAddress));

        waitForIt(
            () -> client.query(CART_READ_MODEL_ID_AND_SHIPPING_ADDRESS, map("cartId", mockCartId)),
            result -> result != null && result.get("CartReadModel") != null
                && mockAddress.get("firstName").equals(result.get("CartReadModel.shippingAddress.firstName")));
      }

      private Object cartMyAddress() {
        GraphQLResponse queryResult = waitForIt(
            () -> client.mutate(CART_MY_ADDRESS, map("cartId", mockCartId, "paginatedVersion", true)),
            result -> result != null && result.get("CartMyAddress") != null);

        return queryResult.get("CartMyAddress");
      }

      private List<Map<String, Object>> expectedItems() {
        return List.of(map(
            "id", mockCartId,
            "myAddress", map(
                "firstName", mockAddress.get("firstName"),
                "lastName", mockAddress.get("lastName"),
                "country", mockAddress.get("country"),
                "state", mockAddress.get("state"),
                "postalCode", mockAddress.get("postalCode"),
                "address", mockAddress.get("address"))));
      }

      @Test
      @Order(1)
      @DisplayName("should correctly fetch calculated fields via GraphQL query")
      void shouldCorrectlyFetchCalculatedFieldsViaGraphQLQuery() {
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(CART_READ_MODELS_WITH_MY_ADDRESS, map("filter", filter("id", "eq", mockCartId))),
            result -> sizeAt(result, "CartReadModels") >= 1);

        List<?> cartData = listAt(queryResult, "CartReadModels");

        assertThat(cartData).isInstanceOf(List.class);
        assertThat(cartData).hasSize(1);
        assertThat(at(cartData.getFirst(), "id")).isEqualTo(mockCartId);
        Map<String, Object> expectedAddress = new LinkedHashMap<>(mockAddress);
        expectedAddress.put("__typename", "Address");
        assertThat(at(cartData.getFirst(), "myAddress")).isEqualTo(expectedAddress);
      }

      @Test
      @Order(2)
      @DisplayName("should correctly fetch calculated fields via code")
      void shouldCorrectlyFetchCalculatedFieldsViaCode() {
        Object cartMyAddress = cartMyAddress();

        assertThat(at(cartMyAddress, "items")).isEqualTo(expectedItems());
        assertThat(at(cartMyAddress, "count")).isEqualTo(1);
        // TypeScript expects '100' on Azure and Local: only Azure's default page size gives it (see the class Javadoc)
        if (isAzure()) {
          assertThat(at(cartMyAddress, "cursor.id")).isEqualTo("100");
        }
      }

      @Test
      @Order(3)
      @DisplayName("should correctly fetch calculated fields via code, with the local cursor of a search without limit")
      void shouldCorrectlyFetchCalculatedFieldsViaCodeWithTheLocalCursorOfASearchWithoutLimit() {
        assumeTrue(isLocal(), "local provider behaviour: cursor = (limit ?? 1) + offset");

        Object cartMyAddress = cartMyAddress();

        assertThat(at(cartMyAddress, "items")).isEqualTo(expectedItems());
        assertThat(at(cartMyAddress, "cursor")).isEqualTo(map("id", "1"));
      }
    }

    /**
     * Not in TypeScript: the filter operators ({@code beginsWith}, {@code not}, {@code gt}/{@code gte}/{@code lt}/
     * {@code lte}, {@code in} and {@code contains} on plain fields), the ascending sort, walking every page of a sorted
     * search with its cursors, and the read-model before hook rewriting the filter of a list search.
     */
    @Nested
    @Order(9)
    @DisplayName("filters, sorting and pages the TypeScript suite leaves uncovered")
    class UncoveredFiltersSortingAndPages {
      private static final int CARTS = 5;
      private final List<String> cartIds = new ArrayList<>();
      private final List<String> firstNames = new ArrayList<>();
      private final String lastName = "Uncovered" + random.alphaNumeric(10);

      /** Five carts with the first names {@code A<random>} to {@code E<random>} and one last name of their own. */
      @BeforeAll
      void provisionCarts() {
        for (int i = 0; i < CARTS; i++) {
          String cartId = random.uuid();
          String firstName = (char) ('A' + i) + random.alphaNumeric(6);
          cartIds.add(cartId);
          firstNames.add(firstName);
          Map<String, Object> address = randomAddress();
          address.put("firstName", firstName);
          address.put("lastName", lastName);

          client.mutate(CHANGE_CART_ITEM, map("cartId", cartId, "productId", random.uuid(), "quantity", i + 1));
          waitForIt(
              () -> client.query(CART_READ_MODEL, map("cartId", cartId)),
              result -> result != null && result.get("CartReadModel") != null);
          client.mutate(UPDATE_SHIPPING_ADDRESS, map("cartId", cartId, "address", address));
        }
        waitForIt(
            () -> client.query(LIST_CART_READ_MODELS,
                map("filter", map("shippingAddress", filter("lastName", "eq", lastName)))),
            result -> sizeAt(result, "ListCartReadModels.items") == CARTS);
      }

      private List<Object> idsOf(Map<String, Object> filter) {
        GraphQLResponse result = client.query(LIST_CART_READ_MODELS, map("filter", filter));
        return listAt(result, "ListCartReadModels.items").stream().map(item -> at(item, "id")).toList();
      }

      @Test
      @Order(1)
      @DisplayName("should filter by beginsWith, contains and in on string fields")
      void shouldFilterByBeginsWithContainsAndInOnStringFields() {
        String second = firstNames.get(1);

        assertThat(idsOf(and(
            map("shippingAddress", filter("lastName", "eq", lastName)),
            map("shippingAddress", filter("firstName", "beginsWith", second.substring(0, 3))))))
            .containsExactly(cartIds.get(1));
        assertThat(idsOf(and(
            map("shippingAddress", filter("lastName", "eq", lastName)),
            map("shippingAddress", filter("firstName", "contains", second.substring(2))))))
            .containsExactly(cartIds.get(1));
        assertThat(idsOf(and(
            map("shippingAddress", filter("lastName", "beginsWith", lastName.substring(0, lastName.length() - 1))),
            map("shippingAddress", filter("firstName", "in", List.of(firstNames.get(0), firstNames.get(4)))))))
            .containsExactlyInAnyOrder(cartIds.get(0), cartIds.get(4));
      }

      @Test
      @Order(2)
      @DisplayName("should filter by not")
      void shouldFilterByNot() {
        assertThat(idsOf(and(
            filter("id", "in", List.of(cartIds.get(0), cartIds.get(1), cartIds.get(2))),
            map("not", filter("id", "eq", cartIds.get(1))))))
            .containsExactlyInAnyOrder(cartIds.get(0), cartIds.get(2));
      }

      @Test
      @Order(3)
      @DisplayName("should filter numbers with gt, gte, lt and lte")
      void shouldFilterNumbersWithGtGteLtAndLte() {
        // products are readable by UserWithEmail only
        String userToken = applicationUnderTest.token().forUser(internet.email(), "UserWithEmail");
        GraphQLClient userClient = applicationUnderTest.graphql().client(userToken);
        List<String> productIds = new ArrayList<>();
        for (int cents : List.of(100, 200, 300)) {
          String productId = random.uuid();
          productIds.add(productId);
          userClient.mutate(CREATE_PRODUCT_WITH_ID, map("productID", productId, "sku", random.uuid(),
              "displayName", commerce.productName(), "description", lorem.paragraph(), "priceInCents", cents,
              "currency", finance.currencyCode()));
        }
        String byPrice = """
            query ListProductReadModels($filter: ListProductReadModelFilter) {
              ListProductReadModels(filter: $filter) {
                items {
                  id
                  price {
                    cents
                  }
                }
              }
            }
            """;
        waitForIt(
            () -> userClient.query(byPrice, map("filter", filter("id", "in", productIds))),
            result -> sizeAt(result, "ListProductReadModels.items") == 3);

        Function<Map<String, Object>, List<Object>> ids = priceFilter -> listAt(
            userClient.query(byPrice, map("filter", and(filter("id", "in", productIds), map("price", priceFilter)))),
            "ListProductReadModels.items").stream().map(item -> at(item, "id")).toList();

        assertThat(ids.apply(filter("cents", "gt", 100))).containsExactlyInAnyOrder(productIds.get(1), productIds.get(2));
        assertThat(ids.apply(filter("cents", "gte", 200))).containsExactlyInAnyOrder(productIds.get(1), productIds.get(2));
        assertThat(ids.apply(filter("cents", "lt", 300))).containsExactlyInAnyOrder(productIds.get(0), productIds.get(1));
        assertThat(ids.apply(filter("cents", "lte", 100))).containsExactly(productIds.get(0));
        assertThat(ids.apply(filter("cents", "in", List.of(100, 300))))
            .containsExactlyInAnyOrder(productIds.get(0), productIds.get(2));
      }

      @Test
      @Order(4)
      @DisplayName("should sort ascending by a nested field")
      void shouldSortAscendingByANestedField() {
        onlyWhen(isAzureOrLocal(), "Only Azure and Local provider implement the sort option");

        GraphQLResponse result = client.query(SORTED_LIST_CART_READ_MODELS_WITH_SHIPPING_ADDRESS, map(
            "filterBy", map("shippingAddress", filter("lastName", "eq", lastName)),
            "sortBy", map("shippingAddress", map("firstName", "ASC"))));

        assertThat(listAt(result, "ListCartReadModels.items").stream()
            .map(item -> at(item, "shippingAddress.firstName")).toList())
            .isEqualTo(firstNames);
      }

      @Test
      @Order(5)
      @DisplayName("should walk every page of a sorted search with the returned cursors")
      void shouldWalkEveryPageOfASortedSearchWithTheReturnedCursors() {
        onlyWhen(isAzureOrLocal(), "Only Azure and Local provider implement the sort option");
        String document = """
            query ListCartReadModels(
              $filterBy: ListCartReadModelFilter
              $sortBy: CartReadModelSortBy
              $limit: Int
              $afterCursor: JSON
            ) {
              ListCartReadModels(filter: $filterBy, sortBy: $sortBy, limit: $limit, afterCursor: $afterCursor) {
                items {
                  id
                }
                count
                cursor
              }
            }
            """;
        List<Object> pagedIds = new ArrayList<>();
        List<Object> counts = new ArrayList<>();
        List<Object> cursors = new ArrayList<>();
        Object cursor = null;
        for (int page = 0; page < CARTS; page++) {
          Map<String, Object> variables = map(
              "filterBy", map("shippingAddress", filter("lastName", "eq", lastName)),
              "sortBy", map("id", "ASC"),
              "limit", 2);
          if (cursor != null) {
            variables.put("afterCursor", cursor);
          }
          GraphQLResponse result = client.query(document, variables);
          List<?> items = listAt(result, "ListCartReadModels.items");
          items.forEach(item -> pagedIds.add(at(item, "id")));
          counts.add(result.get("ListCartReadModels.count"));
          cursor = result.get("ListCartReadModels.cursor");
          cursors.add(cursor == null ? null : at(cursor, "id"));
          if (items.size() < 2 || cursor == null) {
            break;
          }
        }

        assertThat(pagedIds).isEqualTo(cartIds.stream().sorted().map(Object.class::cast).toList());
        assertThat(counts).containsExactly(2, 2, 1);
        if (isLocal()) {
          // the local cursor is the next offset, always present: offset + limit
          assertThat(cursors).containsExactly("2", "4", "6");
        }
      }

      @Test
      @Order(6)
      @DisplayName("should apply the filter rewritten by the before hooks to a list search")
      void shouldApplyTheFilterRewrittenByTheBeforeHooksToAListSearch() {
        client.mutate(CHANGE_CART_ITEM,
            map("cartId", "before-fn-test-modified", "productId", Constants.BEFORE_HOOK_PRODUCT_ID, "quantity", 1));

        GraphQLResponse result = waitForIt(
            () -> client.query(LIST_CART_READ_MODELS, map("filter", filter("id", "eq", "before-fn-test"))),
            response -> sizeAt(response, "ListCartReadModels.items") >= 1);

        assertThat(listAt(result, "ListCartReadModels.items")).hasSize(1);
        assertThat(result.get("ListCartReadModels.items[0].id")).isEqualTo("before-fn-test-modified");
      }
    }
  }

  @Nested
  @Order(2)
  @DisplayName("projections")
  class Projections {

    // Mocha runs the tests of a describe before its nested describes, and so does JUnit with @Nested classes

    @Test
    @Order(1)
    @DisplayName("should project with \"entity joinkey\" and create a new read model")
    void shouldProjectWithEntityJoinkeyAndCreateANewReadModel() {
      addModel();
    }

    @Test
    @Order(2)
    @DisplayName("should project with \"entity joinkey\" and update an existing read model")
    void shouldProjectWithEntityJoinkeyAndUpdateAnExistingReadModel() {
      String fiatId = addModel();
      updateModel(fiatId);

      String johnId = addOwner();
      updateOwner(johnId);
    }

    @Test
    @Order(3)
    @DisplayName("should project with array \"entity joinkey\" and create a new read model")
    void shouldProjectWithArrayEntityJoinkeyAndCreateANewReadModel() {
      String fiatId = addModel();
      String johnId = addOwner();
      String johnFiatId = buy(fiatId, johnId);
      String offer1Id = addOffer(List.of(johnFiatId));
      GraphQLResponse offer = getCarOfferById(offer1Id);
      GraphQLResponse johnFiat = getPurchaseById(johnFiatId);
      assertThat(listAt(johnFiat, "CarPurchasesReadModel.offers")).hasSize(1);
      assertThat(johnFiat.get("CarPurchasesReadModel.offers[0].id")).isEqualTo(offer.get("CarOfferReadModel.id"));
    }

    @Test
    @Order(4)
    @DisplayName("should project with array \"entity joinkey\" and update an existing read model")
    void shouldProjectWithArrayEntityJoinkeyAndUpdateAnExistingReadModel() {
      String fiatId = addModel();
      String johnId = addOwner();
      String lucasId = addOwner();
      String johnFiatId = buy(fiatId, johnId);
      String lucasFiatId = buy(fiatId, lucasId);
      String offer1Id = addOffer(List.of(johnFiatId));
      GraphQLResponse offer1 = getCarOfferById(offer1Id);
      String offer2Id = addOffer(List.of(johnFiatId, lucasFiatId));
      GraphQLResponse offer2 = getCarOfferById(offer2Id);
      GraphQLResponse johnFiat = getPurchaseById(johnFiatId);
      GraphQLResponse lucasFiat = getPurchaseById(lucasFiatId);
      assertThat(listAt(johnFiat, "CarPurchasesReadModel.offers")).hasSize(2);
      assertThat(johnFiat.get("CarPurchasesReadModel.offers[0].id")).isEqualTo(offer1.get("CarOfferReadModel.id"));
      assertThat(johnFiat.get("CarPurchasesReadModel.offers[1].id")).isEqualTo(offer2.get("CarOfferReadModel.id"));
      assertThat(listAt(lucasFiat, "CarPurchasesReadModel.offers")).hasSize(1);
      assertThat(johnFiat.get("CarPurchasesReadModel.offers[0].id")).isEqualTo(offer1.get("CarOfferReadModel.id"));
    }

    @Test
    @Order(5)
    @DisplayName("should project with \"read model joinkey\" and create a new read model")
    void shouldProjectWithReadModelJoinkeyAndCreateANewReadModel() {
      String fiatId = addModel();
      String johnId = addOwner();
      String johnFiatId = buy(fiatId, johnId);
      List<String> allPurchaseIds = List.of(johnFiatId);
      List<?> allPurchases = purchasesByIds(allPurchaseIds);
      assertThat(allPurchases).hasSize(1);
    }

    @Test
    @Order(6)
    @DisplayName("should project with \"read model joinkey\" and update an existing read model")
    void shouldProjectWithReadModelJoinkeyAndUpdateAnExistingReadModel() {
      String fiatId = addModel();
      String johnId = addOwner();
      String lucasId = addOwner();
      String aliceId = addOwner();
      String johnFiatId = buy(fiatId, johnId);
      String lucasFiatId = buy(fiatId, lucasId);
      String aliceFiatId = buy(fiatId, aliceId);
      List<String> allPurchaseIds = List.of(johnFiatId, lucasFiatId, aliceFiatId);
      List<?> allPurchases = purchasesByIds(allPurchaseIds);
      assertThat(allPurchases).hasSize(allPurchaseIds.size());

      updateModel(fiatId);
      updateOwner(johnId);
      List<?> updatedPurchases = purchasesByIds(allPurchaseIds);

      // All MODELS name should be updated
      expectModelNamesUpdated(allPurchases, johnFiatId, updatedPurchases);
      expectModelNamesUpdated(allPurchases, lucasFiatId, updatedPurchases);
      expectModelNamesUpdated(allPurchases, aliceFiatId, updatedPurchases);
      // Only John OWNER name should be updated
      expectFirstOwnerNamesUpdated(allPurchases, johnFiatId, updatedPurchases);
      expectFirstOwnerNamesNotUpdated(allPurchases, lucasFiatId, updatedPurchases);
      expectFirstOwnerNamesNotUpdated(allPurchases, aliceFiatId, updatedPurchases);
    }

    @Test
    @Order(7)
    @DisplayName("should NOT project when \"read model joinkey\" is undefined")
    void shouldNotProjectWhenReadModelJoinkeyIsUndefined() {
      String fiatId = addModel();
      String johnId = addOwner();
      GraphQLResponse originalJohnOwner = waitForId(johnId, "CarOwnerReadModel", null, "name");
      String johnFiatId = buy(fiatId, johnId);
      List<?> purchases = purchasesByIds(List.of(johnFiatId));
      assertThat(purchases).hasSize(1);
      updateOwner(johnId, "SKIP");
      List<?> all = allPurchases();

      // Owner name was updated in the CarOwnerReadModel
      GraphQLResponse newJohnOwner = waitForId(johnId, "CarOwnerReadModel", null, "name");
      assertThat(originalJohnOwner.get("CarOwnerReadModel.name"))
          .isNotEqualTo(newJohnOwner.get("CarOwnerReadModel.name"));

      // Owner name was NOT updated in the Purchases read model
      all.stream().map(p -> at(p, "carOwner.name")).forEach(name -> assertThat(name).isNotEqualTo("SKIP"));
      List<Object> purchaseWithOriginalJohnOwner = all.stream()
          .map(p -> at(p, "carOwner.name"))
          .filter(name -> Objects.equals(name, originalJohnOwner.get("CarOwnerReadModel.name")))
          .toList();
      assertThat(purchaseWithOriginalJohnOwner).hasSize(1);
    }

    @Nested
    @Order(1)
    @DisplayName("projecting two entities")
    class ProjectingTwoEntities {
      private final String mockCartId = random.uuid();
      private final String mockPaymentId = random.uuid();
      private final String mockProductId = random.uuid();
      private final int mockQuantity = random.number(1);
      private final String mockConfirmationToken = random.alphaNumeric(10);

      @BeforeEach
      void provisionCart() {
        // provisioning a cart
        client.mutate(CHANGE_CART_ITEM,
            map("cartId", mockCartId, "productId", mockProductId, "quantity", mockQuantity));
      }

      @Test
      @DisplayName("should project changes for both entities")
      void shouldProjectChangesForBothEntities() {
        GraphQLResponse queryResult = waitForIt(
            () -> client.query(CART_READ_MODEL, map("cartId", mockCartId)),
            result -> result != null && result.get("CartReadModel") != null);

        Map<String, Object> expectedResult = map(
            "__typename", "CartReadModel",
            "id", mockCartId,
            "cartItems", List.of(cartItem(mockProductId, mockQuantity)));

        assertThat(queryResult.get("CartReadModel")).isEqualTo(expectedResult);

        // Make payment
        client.mutate(CONFIRM_PAYMENT,
            map("paymentId", mockPaymentId, "cartId", mockCartId, "confirmationToken", mockConfirmationToken));

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
                """, map("cartId", mockCartId)),
            result -> result != null && result.get("CartReadModel.payment") != null);

        Map<String, Object> expectedUpdatedResult = map(
            "__typename", "CartReadModel",
            "id", mockCartId,
            "cartItems", List.of(cartItem(mockProductId, mockQuantity)),
            "payment", map(
                "__typename", "Payment",
                "confirmationToken", mockConfirmationToken,
                "id", mockPaymentId,
                "cartId", mockCartId));

        assertThat(updatedQueryResult.get("CartReadModel")).isEqualTo(expectedUpdatedResult);
      }
    }

    @Nested
    @Order(2)
    @DisplayName("projecting two entities with array joinKey")
    class ProjectingTwoEntitiesWithArrayJoinKey {
      /** The {@code client} of this describe block, which shadows the module one. */
      private GraphQLClient client;
      private String userToken;

      private final String oneMockProductId = random.uuid();
      private final String twoMockProductId = random.uuid();

      @BeforeAll
      void before() {
        String userEmail = internet.email();
        userToken = applicationUnderTest.token().forUser(userEmail, "UserWithEmail");
        client = applicationUnderTest.graphql().client(userToken);
      }

      @BeforeEach
      void createProducts() {
        // Add item one
        client.mutate(CREATE_PRODUCT_WITH_ID, map(
            "productID", oneMockProductId,
            "sku", random.uuid(),
            "displayName", commerce.productName(),
            "description", lorem.paragraph(),
            "priceInCents", random.number(1),
            "currency", finance.currencyCode()));

        // Add item two
        client.mutate(CREATE_PRODUCT_WITH_ID, map(
            "productID", twoMockProductId,
            "sku", random.uuid(),
            "displayName", commerce.productName(),
            "description", lorem.paragraph(),
            "priceInCents", random.number(1),
            "currency", finance.currencyCode()));
      }

      /** {@code ProductReadModels?.length == 2 && every product has packs.length == packs}. */
      private static boolean bothProductsWithPacks(GraphQLResponse result, int packs) {
        List<?> products = listAt(result, "ProductReadModels");
        return products != null && products.size() == 2
            && products.stream().allMatch(product -> at(product, "packs") instanceof List<?> list
                && list.size() == packs);
      }

      @Test
      @DisplayName("should project changes for both entities")
      void shouldProjectChangesForBothEntities() {
        // Check that new product is available in read model
        GraphQLResponse products = waitForIt(
            () -> client.query(PRODUCT_READ_MODELS_WITH_PACK_IDS,
                map("products", List.of(oneMockProductId, twoMockProductId))),
            result -> bothProductsWithPacks(result, 0));

        assertThat(listAt(products, "ProductReadModels")).hasSize(2);

        String mockPackId = random.uuid();
        String mockPackName = commerce.productName();
        List<String> mockPackProducts = List.of(oneMockProductId, twoMockProductId);

        // Create Pack
        client.mutate(CREATE_PACK, map("packID", mockPackId, "name", mockPackName, "products", mockPackProducts));

        GraphQLResponse updatedQueryResults = waitForIt(
            () -> client.query(PRODUCT_READ_MODELS_WITH_PACKS,
                map("products", List.of(oneMockProductId, twoMockProductId))),
            result -> bothProductsWithPacks(result, 1));

        List<?> updatedProducts = listAt(updatedQueryResults, "ProductReadModels");
        assertThat(updatedProducts).hasSize(2);

        for (Object product : updatedProducts) {
          assertThat(at(product, "packs")).isEqualTo(List.of(map(
              "__typename", "Pack",
              "id", mockPackId,
              "name", mockPackName,
              "products", mockPackProducts)));
        }
      }
    }
  }

  @Nested
  @Order(3)
  @DisplayName("read model authorization")
  class ReadModelAuthorization {

    @Nested
    @Order(1)
    @DisplayName("with an anonymous user")
    class WithAnAnonymousUser {
      private GraphQLClient anonymousClient;

      @BeforeEach
      void createClient() {
        anonymousClient = applicationUnderTest.graphql().client();
      }

      @Nested
      @Order(1)
      @DisplayName("with a public read model")
      class WithAPublicReadModel {
        @Test
        @DisplayName("should be accessible")
        void shouldBeAccessible() {
          assertThatCode(() -> anonymousClient.query(CART_READ_MODEL_ID, map("cartId", "mockCartId")))
              .doesNotThrowAnyException();
        }
      }

      @Nested
      @Order(2)
      @DisplayName("with a read model authorized for certain roles")
      class WithAReadModelAuthorizedForCertainRoles {
        @Test
        @DisplayName("should not be accessible")
        void shouldNotBeAccessible() {
          assertThatThrownBy(() -> anonymousClient.query(PRODUCT_READ_MODEL_ID, map("id", "mockId")))
              .isInstanceOf(GraphQLClientException.class)
              .hasMessageFindingMatch("Access denied for this resource");
        }
      }

      @Nested
      @Order(3)
      @DisplayName("with a read model with a custom authorizer")
      class WithAReadModelWithACustomAuthorizer {
        @Test
        @DisplayName("should not be accessible")
        void shouldNotBeAccessible() {
          assertThatThrownBy(() -> anonymousClient.query(SPECIAL_REPORTS_READ_MODEL_ID, map("id", "mockId")))
              .isInstanceOf(GraphQLClientException.class)
              .hasMessageFindingMatch("You are not allowed to see such insightful report");
        }
      }
    }

    @Nested
    @Order(2)
    @DisplayName("with a user with a role")
    class WithAUserWithARole {
      private GraphQLClient loggedClient;

      @BeforeEach
      void createClient() {
        String userToken = applicationUnderTest.token().forUser(internet.email(), "UserWithEmail");
        loggedClient = applicationUnderTest.graphql().client(userToken);
      }

      @Nested
      @Order(1)
      @DisplayName("with a public read model")
      class WithAPublicReadModel {
        @Test
        @DisplayName("should be accessible")
        void shouldBeAccessible() {
          assertThatCode(() -> loggedClient.query(CART_READ_MODEL_ID, map("cartId", "mockCartId")))
              .doesNotThrowAnyException();
        }
      }

      @Nested
      @Order(2)
      @DisplayName("with a read model authorized for matching roles")
      class WithAReadModelAuthorizedForMatchingRoles {
        @Test
        @DisplayName("should be accessible")
        void shouldBeAccessible() {
          assertThatCode(() -> loggedClient.query(PRODUCT_READ_MODEL_ID, map("id", "mockId")))
              .doesNotThrowAnyException();
        }
      }

      @Nested
      @Order(3)
      @DisplayName("with a read model with a custom authorizer")
      class WithAReadModelWithACustomAuthorizer {
        @Test
        @DisplayName("should not be accessible")
        void shouldNotBeAccessible() {
          assertThatThrownBy(() -> loggedClient.query(SPECIAL_REPORTS_READ_MODEL_ID, map("id", "mockId")))
              .isInstanceOf(GraphQLClientException.class)
              .hasMessageFindingMatch("You are not allowed to see such insightful report");
        }
      }
    }

    @Nested
    @Order(3)
    @DisplayName("with a user that fulfills the custom authorizer")
    class WithAUserThatFulfillsTheCustomAuthorizer {
      private GraphQLClient knowledgeableClient;

      @BeforeEach
      void createClient() {
        String tokenWithSpecialAccess = applicationUnderTest.token().forUser(internet.email(), null,
            TokenOptions.builder().customClaims(Map.of("specialReportAccess", "true")).build());
        knowledgeableClient = applicationUnderTest.graphql().client(tokenWithSpecialAccess);
      }

      @Nested
      @Order(1)
      @DisplayName("with a public read model")
      class WithAPublicReadModel {
        @Test
        @DisplayName("should be accessible")
        void shouldBeAccessible() {
          assertThatCode(() -> knowledgeableClient.query(CART_READ_MODEL_ID, map("cartId", "mockCartId")))
              .doesNotThrowAnyException();
        }
      }

      @Nested
      @Order(2)
      @DisplayName("with a read model authorized for certain roles")
      class WithAReadModelAuthorizedForCertainRoles {
        @Test
        @DisplayName("should not be accessible")
        void shouldNotBeAccessible() {
          assertThatThrownBy(() -> knowledgeableClient.query(PRODUCT_READ_MODEL_ID, map("id", "mockId")))
              .isInstanceOf(GraphQLClientException.class)
              .hasMessageFindingMatch("Access denied for this resource");
        }
      }

      @Nested
      @Order(3)
      @DisplayName("with a read model with a custom authorizer")
      class WithAReadModelWithACustomAuthorizer {
        @Test
        @DisplayName("should be accessible")
        void shouldBeAccessible() {
          assertThatCode(() -> knowledgeableClient.query(SPECIAL_REPORTS_READ_MODEL_ID, map("id", "mockId")))
              .doesNotThrowAnyException();
        }
      }
    }
  }

  // <editor-fold desc="Car-domain helpers (module functions of the TypeScript file, over the module client)">

  private String addModel() {
    return addModel(null, null);
  }

  private String addModel(String name, String brand) {
    String mockModelId = random.uuid();
    String mockName = name != null ? name : random.alphaNumeric(10);
    String mockBrand = brand != null ? brand : random.alphaNumeric(10);

    client.mutate(ADD_CAR_MODEL, map("id", mockModelId, "name", mockName, "brand", mockBrand));
    getCarModelByIdAndName(mockModelId, mockName);
    return mockModelId;
  }

  private String addOwner() {
    return addOwner(null);
  }

  private String addOwner(String name) {
    String mockOwnerId = random.uuid();
    String mockName = name != null ? name : random.alphaNumeric(10);

    client.mutate(ADD_CAR_OWNER, map("id", mockOwnerId, "name", mockName));
    getCarOwnerByIdAndName(mockOwnerId, mockName);
    return mockOwnerId;
  }

  private String addOffer(List<String> purchaseIds) {
    return addOffer(purchaseIds, null);
  }

  private String addOffer(List<String> purchaseIds, String name) {
    String mockOfferId = random.uuid();
    String mockName = name != null ? name : random.alphaNumeric(10);

    client.mutate(ADD_PURCHASE_OFFER, map("id", mockOfferId, "name", mockName, "purchaseIds", purchaseIds));
    getCarOfferByIdAndName(mockOfferId, mockName);
    return mockOfferId;
  }

  private String buy(String modelId, String ownerId) {
    String mockBuyId = random.uuid();
    client.mutate(BUY_CAR, map("id", mockBuyId, "modelId", modelId, "ownerId", ownerId));
    waitForId(mockBuyId, "CarPurchasesReadModel");
    return mockBuyId;
  }

  private List<?> purchasesByIds(List<String> ids) {
    GraphQLResponse result = waitForIt(
        () -> client.query(LIST_CAR_PURCHASES_READ_MODELS, map("filter", filter("id", "in", List.copyOf(ids)))),
        response -> sizeAt(response, "ListCarPurchasesReadModels.items") == ids.size());
    return listAt(result, "ListCarPurchasesReadModels.items");
  }

  private List<?> allPurchases() {
    GraphQLResponse result = client.query(LIST_CAR_PURCHASES_READ_MODELS, map("filter", map()));

    return listAt(result, "ListCarPurchasesReadModels.items");
  }

  private String updateModel(String id) {
    return updateModel(id, null);
  }

  private String updateModel(String id, String name) {
    String mockName = name != null ? name : random.alphaNumeric(10);

    client.mutate(UPDATE_CAR_MODEL_NAME, map("id", id, "name", mockName));
    getCarModelByIdAndName(id, mockName);
    return id;
  }

  private String updateOwner(String id) {
    return updateOwner(id, null);
  }

  private String updateOwner(String id, String name) {
    String mockName = name != null ? name : random.alphaNumeric(10);

    client.mutate(UPDATE_CAR_OWNER_NAME, map("id", id, "name", mockName));
    getCarOwnerByIdAndName(id, mockName);
    return id;
  }

  private GraphQLResponse getCarModelByIdAndName(String id, String modelName) {
    return waitForId(id, "CarModelReadModel",
        result -> result.get("CarModelReadModel") != null
            && modelName.equals(result.get("CarModelReadModel.name")),
        "name");
  }

  private GraphQLResponse getCarOfferByIdAndName(String id, String offerName) {
    return waitForId(id, "CarOfferReadModel",
        result -> result.get("CarOfferReadModel") != null
            && offerName.equals(result.get("CarOfferReadModel.name")),
        "name");
  }

  private GraphQLResponse getCarOfferById(String id) {
    return waitForId(id, "CarOfferReadModel", null, "name purchasesIds");
  }

  private GraphQLResponse getPurchaseById(String id) {
    return waitForId(id, "CarPurchasesReadModel", null,
        "carModel { id name brand } carOwner { id name } offers { id name purchasesIds } ");
  }

  private GraphQLResponse getCarOwnerByIdAndName(String id, String ownerName) {
    return waitForId(id, "CarOwnerReadModel",
        result -> result.get("CarOwnerReadModel") != null
            && ownerName.equals(result.get("CarOwnerReadModel.name")),
        "name");
  }

  private GraphQLResponse waitForId(String expectedId, String readModelName) {
    return waitForId(expectedId, readModelName, null, null);
  }

  /**
   * {@code waitForId(expectedId, readModelName, extraCheck?, extraField?)}: polls the read model by id with a document
   * built like the TypeScript template literal.
   */
  private GraphQLResponse waitForId(
      String expectedId, String readModelName, Predicate<GraphQLResponse> extraCheck, String extraField) {
    String document = """
                query %1$s($id: ID!) {
                  %1$s(id: $id) {
                    id
                    %2$s
                  }
                }
              """.formatted(readModelName, extraField != null ? extraField : "");
    return waitForIt(
        () -> client.query(document, map("id", expectedId)),
        result -> {
          Object readModel = result == null ? null : result.get(readModelName);
          if (extraCheck != null) {
            return readModel != null && expectedId.equals(at(readModel, "id")) && extraCheck.test(result);
          }
          return readModel != null && expectedId.equals(at(readModel, "id"));
        });
  }

  private static void expectModelNamesUpdated(List<?> allPurchases, String purchaseId, List<?> updatedPurchases) {
    Object firstPurchase = getFirstPurchase(allPurchases, purchaseId);
    Object firstUpdatedPurchase = getFirstPurchase(updatedPurchases, purchaseId);
    // ids are the same
    assertThat(at(firstPurchase, "id")).as("same ids").isEqualTo(at(firstUpdatedPurchase, "id"));
    assertThat(at(firstPurchase, "carModel.id")).as("same carModel ids")
        .isEqualTo(at(firstUpdatedPurchase, "carModel.id"));
    // and Names were updated
    assertThat(at(firstPurchase, "carModel.name")).as("distinct carModel names")
        .isNotEqualTo(at(firstUpdatedPurchase, "carModel.name"));
  }

  private static void expectFirstOwnerNamesUpdated(
      List<?> allPurchases, String purchaseId, List<?> updatedPurchases) {
    Object firstPurchase = getFirstPurchase(allPurchases, purchaseId);
    Object firstUpdatedPurchase = getFirstPurchase(updatedPurchases, purchaseId);
    // ids are the same
    assertThat(at(firstPurchase, "id")).as("same ids").isEqualTo(at(firstUpdatedPurchase, "id"));
    assertThat(at(firstPurchase, "carOwner.id")).as("same carOwner ids")
        .isEqualTo(at(firstUpdatedPurchase, "carOwner.id"));
    // and Names were updated
    assertThat(at(firstPurchase, "carOwner.name")).as("distinct carOwner names")
        .isNotEqualTo(at(firstUpdatedPurchase, "carOwner.name"));
  }

  private static void expectFirstOwnerNamesNotUpdated(
      List<?> allPurchases, String purchaseId, List<?> updatedPurchases) {
    Object firstPurchase = getFirstPurchase(allPurchases, purchaseId);
    Object firstUpdatedPurchase = getFirstPurchase(updatedPurchases, purchaseId);
    // ids are the same
    assertThat(at(firstPurchase, "id")).as("same ids").isEqualTo(at(firstUpdatedPurchase, "id"));
    assertThat(at(firstPurchase, "carOwner.id")).as("same carOwner ids")
        .isEqualTo(at(firstUpdatedPurchase, "carOwner.id"));
    // and Names were NOT updated
    assertThat(at(firstPurchase, "carOwner.name")).as("same carOwner names")
        .isEqualTo(at(firstUpdatedPurchase, "carOwner.name"));
  }

  private static Object getFirstPurchase(List<?> purchases, String purchaseId) {
    return purchases.stream().filter(purchase -> purchaseId.equals(at(purchase, "id"))).findFirst().orElse(null);
  }

  // </editor-fold>
}
