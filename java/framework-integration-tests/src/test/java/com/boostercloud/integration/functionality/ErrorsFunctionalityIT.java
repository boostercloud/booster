package com.boostercloud.integration.functionality;

import static com.boostercloud.integration.harness.Faker.internet;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLClientException;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/functionality/errors.integration.ts}: the application's global error
 * handler ({@code AppErrorHandler}) rewrites the errors of command handlers, command before hooks and query handlers,
 * and {@code onError} rewrites the result once more.
 *
 * <p>The expected messages embed {@code JSON.stringify(commandMetadata)} of {@code ChangeCartItem}. Its shape is the
 * TypeScript one, but its contents are the Java metadata (see {@link #CHANGE_CART_ITEM_METADATA_JSON}), so the
 * TypeScript string is replaced by the Java one, the same that {@code ApplicationRegistrationTest} pins in-process.
 * As in TypeScript the dispatch, reducer and projection hooks are not tested here: those errors are logged, not
 * returned.
 */
@EndToEnd
@DisplayName("Global error handler")
class ErrorsFunctionalityIT {

  /**
   * {@code JSON.stringify(commandMetadata)} of {@code ChangeCartItem} as the Java application produces it
   * ({@code ApplicationRegistrationTest.CHANGE_CART_ITEM_METADATA_JSON}). TypeScript's differs in the type
   * information only: {@code importPath} is {@code @boostercloud/framework-types} instead of the Java package of
   * {@code UUID}, the before hooks return {@code Promise<CommandInput>} instead of {@code Record<string, unknown>} and
   * {@code handle} returns {@code Promise<void>} instead of {@code void}. The structure ({@code before} with one
   * {@code null} per hook function, {@code properties} with the non-exposed {@code test}, {@code methods}) and the key
   * order are the same.
   */
  static final String CHANGE_CART_ITEM_METADATA_JSON = """
      {"before":[null,null],"properties":[{"name":"cartId","typeInfo":{"name":"UUID","typeGroup":"Class","isNullable":false,\
      "isGetAccessor":false,"parameters":[],"importPath":"com.boostercloud.framework.types.concepts","typeName":"UUID"}},\
      {"name":"productId","typeInfo":{"name":"UUID","typeGroup":"Class","isNullable":false,"isGetAccessor":false,\
      "parameters":[],"importPath":"com.boostercloud.framework.types.concepts","typeName":"UUID"}},{"name":"quantity",\
      "typeInfo":{"name":"number","typeGroup":"Number","isNullable":false,"isGetAccessor":false,"parameters":[],\
      "typeName":"Number"}},{"name":"test","typeInfo":{"name":"number","typeGroup":"Number","isNullable":false,\
      "isGetAccessor":false,"parameters":[],"typeName":"Number"}}],"methods":[{"name":"beforeFn","typeInfo":{"name":"Record<string,\
       unknown>","typeGroup":"Type","isNullable":false,"isGetAccessor":false,"parameters":[{"name":"string",\
      "typeGroup":"String","isNullable":false,"isGetAccessor":false,"parameters":[],"typeName":"String"},{"name":"unknown",\
      "typeGroup":"Other","isNullable":false,"isGetAccessor":false,"parameters":[]}]}},{"name":"beforeFnV2",\
      "typeInfo":{"name":"Record<string, unknown>","typeGroup":"Type","isNullable":false,"isGetAccessor":false,\
      "parameters":[{"name":"string","typeGroup":"String","isNullable":false,"isGetAccessor":false,"parameters":[],\
      "typeName":"String"},{"name":"unknown","typeGroup":"Other","isNullable":false,"isGetAccessor":false,"parameters":[]}]}},\
      {"name":"handle","typeInfo":{"name":"void","typeGroup":"Other","isNullable":false,"isGetAccessor":false,\
      "parameters":[]}}]}""";

  private static final String CHANGE_CART_ITEM = """
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }
      """;

  private static final String CART_TOTAL_QUANTITY = """
      query CartTotalQuantity($cartId: ID!) {
        CartTotalQuantity(input: { cartId: $cartId })
      }
      """;

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;

  ErrorsFunctionalityIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    String adminEmail = internet.email();
    String authToken = applicationUnderTest.token().forUser(adminEmail, "Admin");
    client = applicationUnderTest.graphql().client(authToken);
  }

  @Nested
  @Order(1)
  @DisplayName("CommandHandler")
  class CommandHandler {

    @Test
    @Order(1)
    @DisplayName("should update error object when handler fails")
    void shouldUpdateErrorObjectWhenHandlerFails() {
      String expectedErrorMessage = Constants.COMMAND_HANDLER_ERROR_CART_MESSAGE
          + "-onCommandHandlerError with metadata: " + CHANGE_CART_ITEM_METADATA_JSON + "-onError";

      // chai-as-promised `rejectedWith(string)`: the error message contains the string
      assertThatThrownBy(() -> client.mutate(CHANGE_CART_ITEM, Map.of(
          "cartId", Constants.COMMAND_HANDLER_ERROR_CART_ID,
          "productId", random.uuid(),
          "quantity", random.number(1))))
          .isInstanceOf(GraphQLClientException.class)
          .hasMessageContaining(expectedErrorMessage);
    }

    @Test
    @Order(2)
    @DisplayName("should ignore error object when handler returns undefined")
    void shouldIgnoreErrorObjectWhenHandlerReturnsUndefined() {
      // `eventually.eql({ data: { ChangeCartItem: true } })`: the whole result, without errors
      assertThat(client.mutate(CHANGE_CART_ITEM, Map.of(
          "cartId", Constants.COMMAND_HANDLER_ERROR_IGNORED_CART_ID,
          "productId", random.uuid(),
          "quantity", random.number(1))).toMap())
          .isEqualTo(Map.of("data", Map.of("ChangeCartItem", true)));
    }

    @Test
    @Order(3)
    @DisplayName("should update error object when onBefore fails")
    void shouldUpdateErrorObjectWhenOnBeforeFails() {
      String expectedErrorMessage = Constants.COMMAND_HANDLER_BEFORE_ERROR_CART_MESSAGE
          + "-onBeforeCommandHandlerError with metadata: " + CHANGE_CART_ITEM_METADATA_JSON + "-onError";

      assertThatThrownBy(() -> client.mutate(CHANGE_CART_ITEM, Map.of(
          "cartId", Constants.COMMAND_HANDLER_BEFORE_ERROR_CART_ID,
          "productId", random.uuid(),
          "quantity", random.number(1))))
          .isInstanceOf(GraphQLClientException.class)
          .hasMessageContaining(expectedErrorMessage);
    }
  }

  @Nested
  @Order(2)
  @DisplayName("QueryHandler")
  class QueryHandler {

    @Test
    @Order(1)
    @DisplayName("should update error object when handler fails")
    void shouldUpdateErrorObjectWhenHandlerFails() {
      String expectedErrorMessage = Constants.QUERY_HANDLER_ERROR_CART_MESSAGE + "-onQueryHandlerError-onError";

      // TypeScript sends the query document with client.mutate
      assertThatThrownBy(() -> client.mutate(CART_TOTAL_QUANTITY,
          Map.of("cartId", Constants.QUERY_HANDLER_ERROR_CART_ID)))
          .isInstanceOf(GraphQLClientException.class)
          .hasMessageContaining(expectedErrorMessage);
    }
  }
}
