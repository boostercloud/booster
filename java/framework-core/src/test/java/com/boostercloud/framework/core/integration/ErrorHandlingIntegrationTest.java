package com.boostercloud.framework.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.integration.app.AppState;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.GraphQLHttpRequest;
import com.boostercloud.framework.core.integration.inmemory.InMemoryRequests.GraphQLResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Failures as a client sees them: the application's global error handler rewriting or swallowing command errors,
 * before-hook and authorization failures, and the errors of malformed GraphQL requests.
 */
class ErrorHandlingIntegrationTest extends IntegrationTestSupport {

  @Test
  void theGlobalErrorHandlerRewritesACommandFailure() {
    Map<String, Object> result = graphQL("mutation { FailingCommand(input: {reason: \"boom\"}) }");

    assertThat(errors(result)).containsExactly(map(
        "message", "Command FailingCommand failed: boom",
        "locations", List.of(Map.of("line", 1, "column", 12)),
        "path", List.of("FailingCommand"),
        "extensions", map("code", "COMMAND_FAILED", "data", Map.of("reason", "boom"))));
    assertThat(AppState.ERRORS).containsExactly("Command FailingCommand failed: boom");
  }

  @Test
  void theGlobalErrorHandlerCanSwallowACommandFailure() {
    Map<String, Object> result = graphQL("mutation { FailingCommand(input: {reason: \"ignore me\"}) }");

    assertThat(data(result)).isEqualTo(Map.of("FailingCommand", true));
    assertThat(AppState.ERRORS).isEmpty();
  }

  @Test
  void beforeHookFailuresGoThroughTheGlobalErrorHandlerButAuthorizationFailuresDoNot() {
    graphQL("mutation { ChangeCartItem(input: {cartId: \"c\", productId: \"p\", quantity: -5}) }");
    graphQL("mutation { CreateProduct(input: {sku: \"S\", displayName: \"S\", price: 1, category: \"c\"}) }");

    assertThat(AppState.ERRORS).containsExactly("The quantity cannot be negative");
  }

  @Test
  void aFailingReducerStopsTheReductionAndIsLoggedWhenTheHandlerHasNoReducerHook() {
    Map<String, Object> result = graphQL(
        "mutation { UpdateProduct(input: {productId: \"ghost\", displayName: \"Ghost\", price: 1}) }",
        TestTokens.admin());

    assertThat(data(result)).isEqualTo(Map.of("UpdateProduct", true));
    assertThat(provider.events().events()).singleElement()
        .satisfies(event -> assertThat(event.typeName()).isEqualTo("ProductUpdated"));
    assertThat(provider.events().snapshots()).isEmpty();
    assertThat(provider.readModels().documents("ProductReadModel")).isEmpty();
    // As in TypeScript, a missing specific hook (onReducerError) rethrows the error, so onError is not called
    assertThat(AppState.ERRORS).isEmpty();
    assertThat(RecordingLogger.INSTANCE.errors()).extracting(RecordingLogger.Line::text).anySatisfy(line -> assertThat(line)
        .contains("Unhandled error inside the global error handler. When handling error NullPointerException")
        .contains("\"current\" is null"));
  }

  @Test
  void syntaxErrorsAreReported() {
    Map<String, Object> result = graphQL("{ CartReadModel(id: ");

    assertThat(errorMessage(result)).startsWith("Invalid syntax");
    assertThat(result).doesNotContainKey("data");
  }

  @Test
  void validationErrorsAreReported() {
    Map<String, Object> result = graphQL("{ CartReadModel(id: \"x\") { nope } }");

    assertThat(errors(result)).singleElement().satisfies(error -> {
      assertThat((String) error.get("message")).contains("nope");
      assertThat(error).containsEntry("extensions", Map.of());
    });
  }

  @Test
  void aCommandWithAMissingInputIsAValidationError() {
    Map<String, Object> result = graphQL("mutation { ChangeCartItem(input: {cartId: \"c\"}) }");

    assertThat(errors(result)).isNotEmpty();
    assertThat(provider.events().events()).isEmpty();
  }

  @Test
  void emptyRequestsAreRejected() {
    GraphQLResponse noBody = (GraphQLResponse) app.boosterServeGraphQL(new GraphQLHttpRequest(Map.of(), null));
    GraphQLResponse noQuery = (GraphQLResponse) app.boosterServeGraphQL(new GraphQLHttpRequest(Map.of(), Map.of()));

    assertThat(errorMessage(noBody.result())).isEqualTo("Received an empty GraphQL body");
    assertThat(errors(noBody.result()).getFirst().get("extensions")).isEqualTo(Map.of("code", "InvalidParameterError"));
    assertThat(errorMessage(noQuery.result())).isEqualTo("Received an empty GraphQL query");
  }

  @Test
  void introspectionCanBeDisabled() {
    assertThat(data(graphQL("{ __schema { queryType { name } } }")))
        .isEqualTo(Map.of("__schema", Map.of("queryType", Map.of("name", "Query"))));

    config.enableGraphQLIntrospection = false;

    assertThat(errorMessage(graphQL("{ __schema { queryType { name } } }"))).isEqualTo(
        "Instrospection queries are disabled. Check the configuration if you want to enable them.");
  }

  @Test
  void severalOperationsNeedAnOperationName() {
    String document = "query A { CartReadModel(id: \"a\") { id } } query B { CartReadModel(id: \"b\") { id } }";

    assertThat(errorMessage(graphQL(document))).startsWith("Could not extract GraphQL operation.");
    Map<String, Object> body = map("query", document, "operationName", "B");
    GraphQLResponse response = (GraphQLResponse) app.boosterServeGraphQL(new GraphQLHttpRequest(Map.of(), body));
    assertThat(data(response.result())).isEqualTo(map("CartReadModel", null));
  }

  @Test
  void aNotFoundReadModelSequenceOrMissingIdIsAValidationError() {
    assertThat(errors(graphQL("{ CartReadModel { id } }"))).singleElement()
        .satisfies(error -> assertThat((String) error.get("message")).contains("id"));
  }
}
