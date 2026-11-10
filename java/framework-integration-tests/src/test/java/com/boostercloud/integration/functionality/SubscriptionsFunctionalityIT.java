package com.boostercloud.integration.functionality;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.functionality.StoredDocuments.object;
import static com.boostercloud.integration.harness.Faker.internet;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.DisconnectableGraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.application.tester.graphql.ObservableSubscription;
import com.boostercloud.application.tester.graphql.SubscriptionObservable;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/functionality/subscriptions.integration.ts}: GraphQL subscriptions over
 * the websocket (the legacy {@code graphql-ws} protocol), counted in the provider's store
 * ({@code count().subscriptions()}, {@code count().connections()}, real on the local provider): unsubscribing,
 * disconnecting, reconnecting, filters (including the read model before hooks) and authorization.
 *
 * <p>Differences, all of them stricter than TypeScript:
 *
 * <ul>
 *   <li>The two {@code the "terminate" operation} tests swallow every error in TypeScript ({@code try { ... } catch
 *       { disconnect() }}), so they can only fail by timing out; here their assertions count, and the clients are
 *       disconnected in {@code finally}.</li>
 *   <li>The promises TypeScript awaits forever (bounded by mocha's one-hour timeout) wait at most
 *       {@link #RESULT_TIMEOUT} here.</li>
 *   <li>{@code keeps the same subscriptions} also checks that a change made after the reconnection reaches every
 *       subscription: in TypeScript (and here, faithfully) the second {@code promisifyNextSubscriptionResult} resolves
 *       at once with the result received before the reconnection, because an Apollo observable hands its latest
 *       result to every new observer.</li>
 * </ul>
 */
@EndToEnd
@DisplayName("subscriptions")
class SubscriptionsFunctionalityIT {

  /** The longest wait for a subscription result. */
  static final Duration RESULT_TIMEOUT = Duration.ofMinutes(2);

  private static final String CART_SUBSCRIPTION = """
      subscription CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
          cartItems {
            productId
            quantity
          }
        }
      }
      """;

  private static final String PRODUCT_SUBSCRIPTION = """
      subscription ProductReadModel($productId: ID!) {
        ProductReadModel(id: $productId) {
          id
          price {
            cents
            currency
          }
        }
      }
      """;

  private static final String CART_FILTERED_SUBSCRIPTION = """
      subscription CartReadModels($filter: CartReadModelSubscriptionFilter) {
        CartReadModels(filter: $filter) {
          id
          cartItems {
            productId
            quantity
          }
          cartItemsIds
        }
      }
      """;

  private static final String CART_FILTERED_SINGLE_ID_SUBSCRIPTION = """
      subscription CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
          cartItems {
            productId
            quantity
          }
          cartItemsIds
        }
      }
      """;

  private static final String CART_MUTATION = """
      mutation ChangeCartItem($cartId: ID!, $productId: ID!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: 2 })
      }
      """;

  private static final String PRODUCT_MUTATION = """
      mutation CreateProduct($sku: String!, $productID: ID) {
        CreateProduct(
          input: {
            sku: $sku
            productID: $productID
            priceInCents: 1.0
            displayName: "product"
            description: "product"
            currency: "ANY"
          }
        )
      }
      """;

  private final ApplicationUnderTest applicationUnderTest;
  private Supplier<Long> countSubscriptions;
  private Supplier<Long> countConnections;

  SubscriptionsFunctionalityIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    countSubscriptions = applicationUnderTest.count()::subscriptions;
    countConnections = applicationUnderTest.count()::connections;
  }

  @Nested
  @Order(1)
  @DisplayName("the \"unsubscribe\" operation")
  class TheUnsubscribeOperation {
    private DisconnectableGraphQLClient client;

    @BeforeAll
    void before() {
      client = applicationUnderTest.graphql().clientWithSubscriptions();
    }

    @AfterAll
    void after() {
      client.disconnect();
    }

    @Test
    @DisplayName("should delete a subscription when the client calls \"unsubscribe\"")
    void shouldDeleteASubscriptionWhenTheClientCallsUnsubscribe() {
      long originalSubscriptionsCount = countSubscriptions.get();

      // Let's create two subscriptions to the same read model
      cartSubscription(client, random.uuid()).subscribe(result -> {});
      ObservableSubscription subscriptionObservable = cartSubscription(client, random.uuid()).subscribe(result -> {});

      // Wait for the subscriptions to arrive
      waitForIt(countSubscriptions, newCount -> newCount == originalSubscriptionsCount + 2);

      // Stop one of the subscription
      subscriptionObservable.unsubscribe();

      // And now check that the new subscriptions count down by one
      waitForIt(countSubscriptions, newCount -> newCount == originalSubscriptionsCount + 1);
    }
  }

  @Nested
  @Order(2)
  @DisplayName("the \"terminate\" operation")
  class TheTerminateOperation {

    @Test
    @Order(1)
    @DisplayName("should delete all subscription of the connectionID when socket is disconnected")
    void shouldDeleteAllSubscriptionOfTheConnectionIdWhenSocketIsDisconnected() {
      DisconnectableGraphQLClient clientA = applicationUnderTest.graphql().clientWithSubscriptions();
      DisconnectableGraphQLClient clientB = applicationUnderTest.graphql().clientWithSubscriptions();
      try {
        long originalSubscriptionsCount = countSubscriptions.get();

        // Let's create one subscription for one client
        cartSubscription(clientA, random.uuid()).subscribe(result -> {});

        // Let's create two subscriptions for another client
        cartSubscription(clientB, random.uuid()).subscribe(result -> {});
        cartSubscription(clientB, random.uuid()).subscribe(result -> {});

        // Wait for the subscriptions to arrive
        waitForIt(countSubscriptions, newCount -> newCount == originalSubscriptionsCount + 3);

        // Now we close the socket of client B and check its 2 subscriptions were deleted
        clientB.disconnect();
        waitForIt(countSubscriptions, newCount -> newCount == originalSubscriptionsCount + 1);

        // Finally, close the socket of client A and check that we are back to the original count of subscriptions
        clientA.disconnect();
        waitForIt(countSubscriptions, newCount -> newCount == originalSubscriptionsCount);
      } finally {
        // TypeScript disconnects in a catch that also swallows the error (see the class comment)
        clientA.disconnect();
        clientB.disconnect();
      }
    }

    @Test
    @Order(2)
    @DisplayName("should delete connection data when socket is disconnected")
    void shouldDeleteConnectionDataWhenSocketIsDisconnected() {
      long connectionsCount = countConnections.get();
      DisconnectableGraphQLClient client = applicationUnderTest.graphql().clientWithSubscriptions();
      try {
        waitForIt(countConnections, newCount -> newCount == connectionsCount + 1);
        client.disconnect();
        waitForIt(countConnections, newCount -> newCount == connectionsCount);
      } finally {
        client.disconnect();
      }
    }
  }

  @Nested
  @Order(3)
  @DisplayName("when socket reconnects ")
  class WhenSocketReconnects {
    private static final int CLIENT_COUNT = 2;
    private List<DisconnectableGraphQLClient> clients;

    @BeforeAll
    void before() {
      clients = new ArrayList<>();
      for (int i = 0; i < CLIENT_COUNT; i++) {
        clients.add(applicationUnderTest.graphql().clientWithSubscriptions());
      }
    }

    @AfterAll
    void after() {
      clients.forEach(DisconnectableGraphQLClient::disconnect);
    }

    @Test
    @DisplayName("keeps the same subscriptions")
    void keepsTheSameSubscriptions() {
      String cartID = random.uuid();
      long originalSubscriptionsCount = countSubscriptions.get();
      List<SubscriptionObservable> observables = clients.stream().map(c -> cartSubscription(c, cartID)).toList();
      List<List<GraphQLResponse>> received = new ArrayList<>();
      for (SubscriptionObservable observable : observables) {
        List<GraphQLResponse> results = new CopyOnWriteArrayList<>();
        received.add(results);
        observable.subscribe(results::add);
      }
      waitForIt(countSubscriptions, newCount -> newCount == originalSubscriptionsCount + CLIENT_COUNT);
      verifySubscriptionsActive(cartID, observables);
      awaitAll(clients.stream().map(DisconnectableGraphQLClient::reconnectAsync).toList());
      verifySubscriptionsActive(cartID, observables);

      // Stricter than TypeScript (see the class comment): the change made after the reconnection (the cart's second
      // item) reaches every subscription
      waitForIt(() -> received, results -> results.stream().allMatch(
          list -> list.stream().anyMatch(result -> result.get("CartReadModel.cartItems[1]") != null)));
    }

    private void verifySubscriptionsActive(String cartID, List<SubscriptionObservable> observables) {
      cartMutation(clients.get(0), cartID);
      awaitAll(observables.stream().map(SubscriptionObservable::nextResult).toList());
    }
  }

  @Nested
  @Order(4)
  @DisplayName("with filters")
  class WithFilters {
    private DisconnectableGraphQLClient client;

    @BeforeAll
    void before() {
      client = applicationUnderTest.graphql().clientWithSubscriptions();
    }

    @AfterAll
    void after() {
      client.disconnect();
    }

    @Test
    @Order(1)
    @DisplayName("get a carts with a specific ID")
    void getACartsWithASpecificId() {
      String cartID = random.uuid();

      long originalSubscriptionsCount = countSubscriptions.get();
      // Let's create two subscriptions to the same read model
      SubscriptionObservable observable = cartFilteredSubscription(client, object("id", object("eq", cartID)));
      // Call the subscribe function to send the subscription to server
      observable.subscribe(result -> {});
      // Wait for the subscriptions to arrive
      waitForIt(countSubscriptions, newCount -> newCount == originalSubscriptionsCount + 1);
      // Check we receive data when the read model is modified
      cartMutation(client, cartID);
      GraphQLResponse result = await(observable.nextResult());
      // A filtered list subscription pushes one read model at a time, not a list
      assertThat(result.get("CartReadModels.id")).isEqualTo(cartID);
    }

    @Test
    @Order(2)
    @DisplayName("filters based on before hooks when user sends filters in the subscription query")
    void filtersBasedOnBeforeHooksWhenUserSendsFiltersInTheSubscriptionQuery() {
      String cartID = "before-fn-test";

      long originalSubscriptionsCount = countSubscriptions.get();
      // Let's create two subscriptions to the same read model
      SubscriptionObservable observable = cartFilteredSubscription(client, object("id", object("eq", cartID)));
      // Call the subscribe function to send the subscription to server
      CompletableFuture<GraphQLResponse> subscriptionPromise = observable.nextResult();
      // Wait for for the subscriptions to arrive
      waitForIt(countSubscriptions, newCount -> newCount == originalSubscriptionsCount + 1);
      // Check we receive data when the read model is modified
      cartMutation(client, "before-fn-test-modified", Constants.BEFORE_HOOK_PRODUCT_ID);

      GraphQLResponse result = await(subscriptionPromise);

      assertThat(result.get("CartReadModels.id")).isEqualTo("before-fn-test-modified");
    }

    @Test
    @Order(3)
    @DisplayName("filters based on single before hooks when user don not send filters but a specific id")
    void filtersBasedOnSingleBeforeHooksWhenUserDonNotSendFiltersButASpecificId() {
      String cartID = "before-fn-test";

      long originalSubscriptionsCount = countSubscriptions.get();
      // Let's create two subscriptions to the same read model
      SubscriptionObservable observable = cartFilteredSingleIdSubscription(client, cartID);
      // Call the subscribe function to send the subscription to server
      CompletableFuture<GraphQLResponse> subscriptionPromise = observable.nextResult();
      // Wait for for the subscriptions to arrive
      waitForIt(countSubscriptions, newCount -> newCount == originalSubscriptionsCount + 1);
      // Check we receive data when the read model is modified
      cartMutation(client, "before-fn-test-modified", Constants.BEFORE_HOOK_PRODUCT_ID);

      GraphQLResponse result = await(subscriptionPromise);

      assertThat(result.get("CartReadModel.id")).isEqualTo("before-fn-test-modified");
    }

    @Test
    @Order(4)
    @DisplayName("get the carts with an specific product id")
    @SuppressWarnings("unchecked")
    void getTheCartsWithAnSpecificProductId() {
      String cartID = random.uuid();
      String productId = random.uuid();

      long originalSubscriptionsCount = countSubscriptions.get();
      // Let's create two subscriptions to the same read model
      SubscriptionObservable observable = cartFilteredSubscription(client,
          object("cartItems", object("includes", object("productId", productId, "quantity", 2))));
      // Call the subscribe function to send the subscription to server
      observable.subscribe(result -> {});
      // Wait for for the subscriptions to arrive
      waitForIt(countSubscriptions, newCount -> newCount == originalSubscriptionsCount + 1);
      // Check we receive data when the read model is modified
      cartMutation(client, cartID, productId);
      GraphQLResponse result = await(observable.nextResult());
      assertThat(result.get("CartReadModels.id")).isEqualTo(cartID);

      List<Map<String, Object>> cartItems = (List<Map<String, Object>>) result.get("CartReadModels.cartItems");
      boolean isWellFiltered = cartItems.stream().anyMatch(cartItem -> productId.equals(cartItem.get("productId")));
      assertThat(isWellFiltered).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("get the cart filtering by an array of strings")
    void getTheCartFilteringByAnArrayOfStrings() {
      String cartID = random.uuid();
      String productId = random.uuid();

      long originalSubscriptionsCount = countSubscriptions.get();
      // Let's create two subscriptions to the same read model
      SubscriptionObservable observable =
          cartFilteredSubscription(client, object("cartItemsIds", object("includes", productId)));
      // Call the subscribe function to send the subscription to server
      observable.subscribe(result -> {});
      // Wait for for the subscriptions to arrive
      waitForIt(countSubscriptions, newCount -> newCount == originalSubscriptionsCount + 1);
      // Check we receive data when the read model is modified
      cartMutation(client, cartID, productId);
      GraphQLResponse result = await(observable.nextResult());

      assertThat(result.get("CartReadModels.id")).isEqualTo(cartID);

      List<?> cartItemsIds = (List<?>) result.get("CartReadModels.cartItemsIds");
      boolean isWellFiltered = cartItemsIds.stream().anyMatch(productId::equals);
      assertThat(isWellFiltered).isTrue();
    }
  }

  @Nested
  @Order(5)
  @DisplayName("readmodel authorization")
  class ReadmodelAuthorization {

    @Nested
    @Order(1)
    @DisplayName("with an anonymous user")
    class WithAnAnonymousUser {
      private DisconnectableGraphQLClient client;

      @BeforeEach
      void beforeEach() {
        client = applicationUnderTest.graphql().clientWithSubscriptions();
      }

      @AfterEach
      void afterEach() {
        client.disconnect();
      }

      @Nested
      @DisplayName("with a read model authorized for matching roles")
      class WithAReadModelAuthorizedForMatchingRoles {

        @Test
        @DisplayName("should not be accessible")
        void shouldNotBeAccessible() {
          assertAccessDenied(client);
        }
      }
    }

    @Nested
    @Order(2)
    @DisplayName("with a user without the required role")
    class WithAUserWithoutTheRequiredRole {
      private DisconnectableGraphQLClient loggedClient;

      @BeforeEach
      void beforeEach() {
        String userToken = applicationUnderTest.token().forUser(internet.email(), "UserThatHasNoBusinesWithProducts");
        loggedClient = applicationUnderTest.graphql().clientWithSubscriptions(userToken);
      }

      @AfterEach
      void afterEach() {
        loggedClient.disconnect();
      }

      @Nested
      @DisplayName("with a read model authorized for matching roles")
      class WithAReadModelAuthorizedForMatchingRoles {

        @Test
        @DisplayName("should not be accessible")
        void shouldNotBeAccessible() {
          assertAccessDenied(loggedClient);
        }
      }
    }

    @Nested
    @Order(3)
    @DisplayName("with a user with the required role")
    class WithAUserWithTheRequiredRole {
      private DisconnectableGraphQLClient loggedClient;

      @BeforeEach
      void beforeEach() {
        String userToken = applicationUnderTest.token().forUser(internet.email(), "UserWithEmail");
        loggedClient = applicationUnderTest.graphql().clientWithSubscriptions(userToken);
      }

      @AfterEach
      void afterEach() {
        loggedClient.disconnect();
      }

      @Nested
      @DisplayName("with a read model authorized for matching roles")
      class WithAReadModelAuthorizedForMatchingRoles {

        @Test
        @DisplayName("should be accessible")
        void shouldBeAccessible() {
          String productId = random.uuid();

          long originalSubscriptionsCount = countSubscriptions.get();
          SubscriptionObservable observable = productSubscription(loggedClient, productId);
          observable.subscribe(result -> {});
          waitForIt(countSubscriptions, newCount -> newCount == originalSubscriptionsCount + 1);
          productMutation(loggedClient, productId);
          GraphQLResponse result = await(observable.nextResult());
          assertThat(result.get("ProductReadModel.id")).isEqualTo(productId);
        }
      }
    }

    /** The two {@code should not be accessible} tests: the subscription fails with the authorization error. */
    private void assertAccessDenied(DisconnectableGraphQLClient client) {
      String productId = random.uuid();

      SubscriptionObservable observable = productSubscription(client, productId);
      AtomicReference<Throwable> error = new AtomicReference<>();
      observable.subscribe(result -> {}, error::set);
      waitForIt(error::get,
          failure -> failure != null && "Access denied for this resource".equals(failure.getMessage()));
    }
  }

  private static SubscriptionObservable cartSubscription(DisconnectableGraphQLClient client, String cartID) {
    return client.subscribe(CART_SUBSCRIPTION, Map.of("cartId", cartID));
  }

  private static SubscriptionObservable productSubscription(DisconnectableGraphQLClient client, String productId) {
    return client.subscribe(PRODUCT_SUBSCRIPTION, Map.of("productId", productId));
  }

  private static SubscriptionObservable cartFilteredSubscription(
      DisconnectableGraphQLClient client, Map<String, Object> filter) {
    return client.subscribe(CART_FILTERED_SUBSCRIPTION, Map.of("filter", filter));
  }

  private static SubscriptionObservable cartFilteredSingleIdSubscription(
      DisconnectableGraphQLClient client, String cartId) {
    return client.subscribe(CART_FILTERED_SINGLE_ID_SUBSCRIPTION, Map.of("cartId", cartId, "random", "variable"));
  }

  private static void cartMutation(DisconnectableGraphQLClient client, String cartID) {
    cartMutation(client, cartID, random.uuid());
  }

  private static void cartMutation(DisconnectableGraphQLClient client, String cartID, String productId) {
    client.mutate(CART_MUTATION, Map.of("cartId", cartID, "productId", productId));
  }

  private static void productMutation(DisconnectableGraphQLClient client, String productId) {
    String sku = random.uuid();
    client.mutate(PRODUCT_MUTATION, Map.of("sku", sku, "productID", productId));
  }

  /** {@code await promise}, bounded by {@link #RESULT_TIMEOUT}. */
  private static <T> T await(CompletableFuture<T> promise) {
    try {
      return promise.get(RESULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for a subscription result", e);
    } catch (TimeoutException e) {
      throw new AssertionError("No subscription result within " + RESULT_TIMEOUT, e);
    } catch (ExecutionException e) {
      throw new AssertionError("The subscription failed: " + e.getCause(), e.getCause());
    }
  }

  /** {@code await Promise.all(promises)} (every one must be fulfilled), bounded by {@link #RESULT_TIMEOUT}. */
  private static void awaitAll(List<? extends CompletableFuture<?>> promises) {
    await(CompletableFuture.allOf(promises.toArray(CompletableFuture[]::new)));
  }
}
