package com.boostercloud.integration.functionality;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.harness.Faker.internet;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.harness.AppProcess;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import com.boostercloud.integration.helper.AppHelper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Java-only companion of {@code functionality/errors.integration.ts}, for the three hooks of the global error handler
 * that the TypeScript suite leaves as {@code TODO} because their errors are not returned to the client but logged:
 * {@code onDispatchEventHandlerError} (an event handler fails), {@code onReducerError} (a reducer fails) and
 * {@code onProjectionError} (a projection fails). It checks that the application's hooks rewrite the error (then
 * {@code onError}), that the rewritten error reaches the application log, and what the failure leaves in the stores.
 *
 * <p>The log is the output of the launched process, so the suite runs on the local provider only. It has an
 * application of its own: a cart whose reducer fails cannot be read any more, and the shared application's data
 * migration test reads every cart.
 */
@EndToEnd(EndToEnd.Application.OWN)
@EnabledIfEnvironmentVariable(named = AppHelper.TESTED_PROVIDER, matches = AppHelper.LOCAL,
    disabledReason = "reads the log of the application process the harness launches on the local provider")
@DisplayName("Global error handler (errors that are logged)")
class LoggedErrorsFunctionalityIT {

  private static final String MOVE_STOCK = """
      mutation MoveStock($productID: String!, $origin: String!, $destination: String!, $quantity: Float!) {
        MoveStock(input: { productID: $productID, origin: $origin, destination: $destination, quantity: $quantity })
      }
      """;

  private static final String CHANGE_CART_ITEM = """
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }
      """;

  private static final String CART_READ_MODEL = """
      query CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
        }
      }
      """;

  private final ApplicationUnderTest applicationUnderTest;
  private final AppProcess app;
  private GraphQLClient client;

  LoggedErrorsFunctionalityIT(ApplicationUnderTest applicationUnderTest, AppProcess app) {
    this.applicationUnderTest = applicationUnderTest;
    this.app = app;
  }

  @BeforeAll
  void before() {
    String authToken = applicationUnderTest.token().forUser(internet.email(), "Admin");
    client = applicationUnderTest.graphql().client(authToken);
  }

  @Nested
  @Order(1)
  @DisplayName("DispatchEventHandler")
  class DispatchEventHandler {

    @Test
    @DisplayName("logs the error returned by onDispatchEventHandlerError and keeps the event")
    void logsTheErrorReturnedByOnDispatchEventHandlerErrorAndKeepsTheEvent() {
      int logOffset = app.output().length();

      GraphQLResponse response = client.mutate(MOVE_STOCK, Map.of("productID", Constants.DISPATCH_EVENT_ERROR_CART_ID,
          "origin", "provider", "destination", "warehouse", "quantity", 1));

      // the command succeeds: the handler of its event fails later, during the dispatch
      assertThat(response.get("MoveStock")).isEqualTo(true);
      // "undefined" as in TypeScript, whose event processor passes `e.eventHandlerMetadata`, a property the error the
      // handler threw does not have, as the metadata (JSON.stringify(undefined) in a template literal)
      awaitLoggedError(logOffset, Constants.DISPATCH_ERROR_CART_MESSAGE
          + "-onDispatchEventHandlerError with metadata: undefined-onError");
      List<Map<String, Object>> stockEvents =
          applicationUnderTest.query().events("Stock-" + Constants.DISPATCH_EVENT_ERROR_CART_ID + "-event");
      assertThat(stockEvents).extracting(event -> event.get("typeName")).contains("StockMoved");
      // HandleAvailability threw before registering ProductAvailabilityChanged
      assertThat(applicationUnderTest.query().events("Product-" + Constants.DISPATCH_EVENT_ERROR_CART_ID + "-event"))
          .isEmpty();
    }
  }

  @Nested
  @Order(2)
  @DisplayName("onReducerError")
  class OnReducerError {

    @Test
    @DisplayName("logs the error returned by onReducerError and projects nothing")
    void logsTheErrorReturnedByOnReducerErrorAndProjectsNothing() {
      int logOffset = app.output().length();

      GraphQLResponse response = client.mutate(CHANGE_CART_ITEM, Map.of("cartId", Constants.REDUCER_ERROR_CART_ID,
          "productId", random.uuid(), "quantity", 1));

      assertThat(response.get("ChangeCartItem")).isEqualTo(true);
      // JSON.stringify(reducerMetadata): the class (a function) is left out
      awaitLoggedError(logOffset, Constants.REDUCER_ERROR_CART_MESSAGE
          + "-onReducerError with metadata: {\"methodName\":\"changeItem\"}-onError");
      assertThat(applicationUnderTest.query().events("Cart-" + Constants.REDUCER_ERROR_CART_ID + "-event"))
          .extracting(event -> event.get("typeName")).containsExactly("CartItemChanged");
      assertThat(applicationUnderTest.query().events("Cart-" + Constants.REDUCER_ERROR_CART_ID + "-snapshot"))
          .isEmpty();
      assertThat(client.query(CART_READ_MODEL, Map.of("cartId", Constants.REDUCER_ERROR_CART_ID))
          .get("CartReadModel")).isNull();
    }
  }

  @Nested
  @Order(3)
  @DisplayName("onProjectionError")
  class OnProjectionError {

    @Test
    @DisplayName("logs the error returned by onProjectionError and does not store the read model")
    void logsTheErrorReturnedByOnProjectionErrorAndDoesNotStoreTheReadModel() {
      int logOffset = app.output().length();

      GraphQLResponse response = client.mutate(CHANGE_CART_ITEM, Map.of("cartId", Constants.PROJECTION_ERROR_CART_ID,
          "productId", random.uuid(), "quantity", 1));

      assertThat(response.get("ChangeCartItem")).isEqualTo(true);
      // JSON.stringify(projectionMetadata): the class (a function) is left out
      awaitLoggedError(logOffset, Constants.PROJECTION_ERROR_CART_MESSAGE
          + "-onProjectionError with metadata: {\"methodName\":\"updateWithCart\",\"joinKey\":\"id\"}-onError");
      assertThat(client.query(CART_READ_MODEL, Map.of("cartId", Constants.PROJECTION_ERROR_CART_ID))
          .get("CartReadModel")).isNull();
    }
  }

  /**
   * Waits (at most a minute) until the application logs the error message. The log renders the logged values as JSON,
   * so the message is looked for in the unescaped output written since {@code logOffset}.
   */
  private void awaitLoggedError(int logOffset, String message) {
    waitForIt(() -> app.output().substring(logOffset).replace("\\\"", "\""),
        output -> output.contains(message) ? Boolean.TRUE : "not logged: " + message, 500, 60000);
  }
}
