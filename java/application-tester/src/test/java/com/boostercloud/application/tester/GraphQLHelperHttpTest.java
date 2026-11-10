package com.boostercloud.application.tester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;

import com.boostercloud.application.tester.fake.FakeGraphQLServer;
import com.boostercloud.application.tester.fake.FakeGraphQLServer.FakeResponse;
import com.boostercloud.application.tester.fake.FakeGraphQLServer.RecordedRequest;
import com.boostercloud.application.tester.graphql.ErrorPolicy;
import com.boostercloud.application.tester.graphql.FetchError;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLClientException;
import com.boostercloud.application.tester.graphql.GraphQLClientOptions;
import com.boostercloud.application.tester.graphql.GraphQLError;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.application.tester.graphql.ServerError;
import com.boostercloud.application.tester.graphql.ServerParseError;
import com.boostercloud.application.tester.graphql.SubscriptionObservable;
import com.boostercloud.framework.types.Logger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GraphQLHelperHttpTest {

  private static final String CHANGE_CART_ITEM = """
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }
      """;

  private static final String CART_QUERY = """
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

  private FakeGraphQLServer server;
  private GraphQLHelper graphql;

  @BeforeEach
  void start() {
    server = FakeGraphQLServer.start();
    graphql = helperFor(server, GraphQLClientOptions.builder().socketLogger(Logger.NOOP).build());
  }

  @AfterEach
  void stop() {
    server.close();
  }

  static GraphQLHelper helperFor(FakeGraphQLServer server, GraphQLClientOptions options) {
    ApplicationOutputs outputs = new ApplicationOutputs(server.graphqlUrl(), server.websocketUrl(), server.healthUrl());
    return new GraphQLHelper(ProviderTestHelper.of(outputs, mock(Counters.class), mock(Queries.class)), options);
  }

  private RecordedRequest onlyRequest() {
    assertThat(server.requests()).hasSize(1);
    return server.requests().getFirst();
  }

  private static Map<String, Object> variables(Object... keyValues) {
    Map<String, Object> variables = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      variables.put((String) keyValues[i], keyValues[i + 1]);
    }
    return variables;
  }

  @Nested
  @DisplayName("requests")
  class Requests {

    @Test
    @DisplayName("are POSTs of {operationName, variables, query} with JSON headers and no Authorization when anonymous")
    void anonymousRequest() {
      server.onHttp(request -> FakeResponse.json(200, "{\"data\":{\"ChangeCartItem\":true}}"));

      GraphQLResponse response = graphql.client()
          .mutate(CHANGE_CART_ITEM, variables("cartId", "c1", "productId", "p1", "quantity", 2));

      assertThat(response.get("ChangeCartItem")).isEqualTo(true);
      RecordedRequest request = onlyRequest();
      assertThat(request.method()).isEqualTo("POST");
      assertThat(request.path()).isEqualTo("/graphql");
      assertThat(request.headers()).containsEntry("content-type", "application/json").containsEntry("accept", "*/*");
      assertThat(request.headers()).doesNotContainKey("authorization");
      Map<String, Object> body = request.json();
      assertThat(body.keySet()).containsExactly("operationName", "variables", "query");
      assertThat(body).containsEntry("operationName", "ChangeCartItem");
      assertThat(body.get("variables")).isEqualTo(Map.of("cartId", "c1", "productId", "p1", "quantity", 2));
      assertThat((String) body.get("query")).startsWith("mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!)");
    }

    @Test
    @DisplayName("send Authorization: Bearer <token> with a token, and nothing for an empty token")
    void tokens() {
      graphql.client("my-token").query("query Q { a }");
      graphql.client("").query("query Q { a }");
      graphql.client((String) null).query("query Q { a }");

      List<RecordedRequest> requests = server.requests();
      assertThat(requests.get(0).headers()).containsEntry("authorization", "Bearer my-token");
      assertThat(requests.get(1).headers()).doesNotContainKey("authorization");
      assertThat(requests.get(2).headers()).doesNotContainKey("authorization");
    }

    @Test
    @DisplayName("evaluate a token function on every operation")
    void tokenFunction() {
      AtomicInteger counter = new AtomicInteger();
      GraphQLClient client = graphql.client(() -> "token-" + counter.incrementAndGet());
      client.query("query Q { a }");
      client.mutate("mutation M { b }");

      assertThat(server.requests()).extracting(request -> request.headers().get("authorization"))
          .containsExactly("Bearer token-1", "Bearer token-2");
    }

    @Test
    @DisplayName("add __typename to nested selection sets, like Apollo's InMemoryCache")
    void typename() {
      graphql.client().query(CART_QUERY, Map.of("cartId", "c1"));

      String query = (String) onlyRequest().json().get("query");
      assertThat(query.replaceAll("\\s+", " ").trim()).isEqualTo(
          "query CartReadModel($cartId: ID!) { CartReadModel(id: $cartId) { id cartItems { productId quantity"
              + " __typename } __typename } }");
    }

    @Test
    @DisplayName("can leave the document as written")
    void withoutTypename() {
      graphql.withOptions(GraphQLClientOptions.builder().addTypename(false).build())
          .client().query(CART_QUERY, Map.of("cartId", "c1"));
      assertThat((String) onlyRequest().json().get("query")).doesNotContain("__typename");
    }

    @Test
    @DisplayName("strip unused variables, merge defaults, keep nulls and omit the name of anonymous operations")
    void variablesHandling() {
      Map<String, Object> sent = variables("cartId", "c1", "random", "variable", "nothing", null);
      graphql.client().query("query ($cartId: ID!, $nothing: String, $limit: Int = 3) { a(id: $cartId, n: $nothing, l: $limit) }", sent);

      Map<String, Object> body = onlyRequest().json();
      assertThat(body).doesNotContainKey("operationName");
      Map<String, Object> expected = variables("limit", 3, "cartId", "c1", "nothing", null);
      assertThat(body.get("variables")).isEqualTo(expected);
    }

    @Test
    @DisplayName("can send unused variables too")
    void includeUnusedVariables() {
      graphql.withOptions(GraphQLClientOptions.builder().includeUnusedVariables(true).build())
          .client().query("query Q($a: ID) { x(id: $a) }", variables("a", "1", "b", "2"));
      assertThat(onlyRequest().json().get("variables")).isEqualTo(Map.of("a", "1", "b", "2"));
    }

    @Test
    @DisplayName("mutate accepts query documents")
    void mutateWithQueryDocument() {
      server.onHttp(request -> FakeResponse.json(200, "{\"data\":{\"a\":1}}"));
      assertThat(graphql.client().mutate("query Q { a }").get("a")).isEqualTo(1);
    }

    @Test
    @DisplayName("invalid documents fail before anything is sent")
    void invalidDocuments() {
      assertThatThrownBy(() -> graphql.client().query("query A { a } query B { b }"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Ambiguous GraphQL document: contains 2 operations");
      assertThatThrownBy(() -> graphql.client().query("{")).isInstanceOf(IllegalArgumentException.class);
      assertThat(server.requests()).isEmpty();
    }
  }

  @Nested
  @DisplayName("responses")
  class Responses {

    @Test
    @DisplayName("expose the raw data, navigable by path")
    void data() {
      server.onHttp(request -> FakeResponse.json(200, """
          {"data":{"CartReadModel":{"id":"c1","cartItems":[{"productId":"p1","quantity":2,"__typename":"CartItem"}],
          "__typename":"CartReadModel"}},"extensions":{"cost":1}}"""));

      GraphQLResponse response = graphql.client().query(CART_QUERY, Map.of("cartId", "c1"));

      assertThat(response.get("CartReadModel.id")).isEqualTo("c1");
      assertThat(response.get("CartReadModel.cartItems[0]"))
          .isEqualTo(Map.of("productId", "p1", "quantity", 2, "__typename", "CartItem"));
      assertThat(response.get("CartReadModel.cartItems[0].quantity", Double.class)).isEqualTo(2.0);
      assertThat(response.get("CartReadModel.missing.deeper")).isNull();
      assertThat(response.get("CartReadModel.cartItems[3]")).isNull();
      assertThat(response.errors()).isEmpty();
      assertThat(response.extensions()).containsEntry("cost", 1);
      assertThat(response.toMap().keySet()).containsExactly("data", "extensions");
      assertThatThrownBy(() -> response.get("a..b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("with errors throw an ApolloError-like exception carrying the raw errors")
    void graphQLErrors() {
      server.onHttp(request -> FakeResponse.json(200, """
          {"errors":[{"message":"TokenExpiredError: jwt expired\\nTokenExpiredError: jwt expired",
          "extensions":{"code":"BoosterTokenExpiredError"}}]}"""));

      GraphQLClientException error = catchThrowableOfType(
          GraphQLClientException.class, () -> graphql.client("expired").query("query Q { a }"));

      assertThat(error.graphQLErrors()).extracting(GraphQLError::toMap).containsExactly(Map.of(
          "message", "TokenExpiredError: jwt expired\nTokenExpiredError: jwt expired",
          "extensions", Map.of("code", "BoosterTokenExpiredError")));
      assertThat(error.graphQLErrors().getFirst().code()).isEqualTo("BoosterTokenExpiredError");
      assertThat(error).hasMessage("TokenExpiredError: jwt expired\nTokenExpiredError: jwt expired");
      assertThat(error.networkError()).isEmpty();
    }

    @Test
    @DisplayName("with several errors join their messages and keep paths")
    void severalErrors() {
      server.onHttp(request -> FakeResponse.json(200, """
          {"errors":[{"message":"Before hook throwing exception-onError","path":["ChangeCartItem"]},{"path":[]}],
          "data":null}"""));

      GraphQLClientException error =
          catchThrowableOfType(GraphQLClientException.class, () -> graphql.client().mutate("mutation M { a }"));

      assertThat(error).hasMessage("Before hook throwing exception-onError\nundefined");
      assertThat(error.graphQLErrors().getFirst().path()).containsExactly("ChangeCartItem");
      assertThat(error.graphQLErrors().getFirst().message()).isEqualTo("Before hook throwing exception-onError");
    }

    @Test
    @DisplayName("with errors are returned with errorPolicy all, and without errors with ignore")
    void errorPolicies() {
      server.onHttp(request -> FakeResponse.json(200, "{\"data\":{\"a\":1,\"b\":null},\"errors\":[{\"message\":\"b failed\"}]}"));

      GraphQLResponse all = graphql.client().query("query Q { a b }", null, ErrorPolicy.ALL);
      assertThat(all.get("a")).isEqualTo(1);
      assertThat(all.errors()).extracting(GraphQLError::message).containsExactly("b failed");

      GraphQLResponse ignore = graphql.client().mutate("mutation M { a b }", null, ErrorPolicy.IGNORE);
      assertThat(ignore.get("a")).isEqualTo(1);
      assertThat(ignore.errors()).isEmpty();
    }

    @Test
    @DisplayName("with a status >= 300 fail with a ServerError network error")
    void badStatus() {
      server.onHttp(request -> FakeResponse.json(500, "{\"message\":\"boom\"}"));

      GraphQLClientException error =
          catchThrowableOfType(GraphQLClientException.class, () -> graphql.client().query("query Q { a }"));

      assertThat(error).hasMessage("Response not successful: Received status code 500");
      ServerError serverError = (ServerError) error.networkError().orElseThrow();
      assertThat(serverError.statusCode()).isEqualTo(500);
      assertThat(serverError.result()).isEqualTo(Map.of("message", "boom"));
      assertThat(serverError.bodyText()).isEqualTo("{\"message\":\"boom\"}");
      assertThat(error.graphQLErrors()).isEmpty();
    }

    @Test
    @DisplayName("with a bad status but data and errors surface the GraphQL errors")
    void badStatusWithResult() {
      server.onHttp(request -> FakeResponse.json(401, "{\"data\":{\"a\":1},\"errors\":[{\"message\":\"denied\"}]}"));

      GraphQLClientException none =
          catchThrowableOfType(GraphQLClientException.class, () -> graphql.client().query("query Q { a }"));
      assertThat(none).hasMessage("denied");
      assertThat(none.networkError()).isEmpty();

      GraphQLClientException all = catchThrowableOfType(
          GraphQLClientException.class, () -> graphql.client().query("query Q { a }", null, ErrorPolicy.ALL));
      assertThat(all).hasMessage("Response not successful: Received status code 401");
    }

    @Test
    @DisplayName("that are not JSON fail with a ServerParseError")
    void notJson() {
      server.onHttp(request -> new FakeResponse(200, "text/html", "<html></html>"));

      GraphQLClientException error =
          catchThrowableOfType(GraphQLClientException.class, () -> graphql.client().query("query Q { a }"));

      ServerParseError parseError = (ServerParseError) error.networkError().orElseThrow();
      assertThat(parseError.statusCode()).isEqualTo(200);
      assertThat(parseError.bodyText()).isEqualTo("<html></html>");
      assertThat(error.getMessage()).isEqualTo(parseError.getMessage()).isNotBlank();
    }

    @Test
    @DisplayName("without data nor errors fail like Apollo")
    void missingResult() {
      server.onHttp(request -> FakeResponse.json(200, "{\"result\":1}"));

      assertThatThrownBy(() -> graphql.client().query("query Named { a }"))
          .isInstanceOf(GraphQLClientException.class)
          .hasMessage("Server response was missing for query 'Named'.");
      assertThatThrownBy(() -> graphql.client().query("{ a }"))
          .hasMessage("Server response was missing for query 'undefined'.");
    }

    @Test
    @DisplayName("that cannot be fetched fail with a FetchError")
    void connectionRefused() {
      server.stopServer();

      GraphQLClientException error =
          catchThrowableOfType(GraphQLClientException.class, () -> graphql.client().query("query Q { a }"));

      assertThat(error.networkError()).get().isInstanceOf(FetchError.class);
      assertThat(error).hasMessageStartingWith("request to " + server.graphqlUrl() + " failed, reason: ");
    }

    @Test
    @DisplayName("time out with a FetchError when a request timeout is set")
    void requestTimeout() {
      server.onHttp(request -> {
        Sleep.sleep(2000);
        return FakeResponse.json(200, "{\"data\":{}}");
      });
      GraphQLClient client =
          graphql.withOptions(GraphQLClientOptions.builder().requestTimeout(Duration.ofMillis(200)).build()).client();

      GraphQLClientException error =
          catchThrowableOfType(GraphQLClientException.class, () -> client.query("query Q { a }"));

      assertThat(error.networkError()).get().isInstanceOf(FetchError.class);
    }
  }

  @Nested
  @DisplayName("subscribe without a websocket")
  class HttpSubscribe {

    @Test
    @DisplayName("posts the operation once, emits its result and completes")
    void singleResult() throws Exception {
      server.onHttp(request -> FakeResponse.json(200, "{\"data\":{\"CartReadModel\":{\"id\":\"c1\"}}}"));
      List<Object> events = new CopyOnWriteArrayList<>();

      SubscriptionObservable observable =
          graphql.client().subscribe("subscription S($id: ID!) { CartReadModel(id: $id) { id } }", Map.of("id", "c1"));
      observable.subscribe(result -> events.add(result.get("CartReadModel.id")), events::add, () -> events.add("done"));

      org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> events.size() == 2);
      assertThat(events).containsExactly("c1", "done");
      // a late observer gets the latest result and the completion
      assertThat(observable.nextResult().get(5, TimeUnit.SECONDS).get("CartReadModel.id")).isEqualTo("c1");
      assertThat(server.requests()).hasSize(1);
    }

    @Test
    @DisplayName("delivers GraphQL errors as ApolloError-like failures and network failures as they are")
    void failures() throws Exception {
      server.onHttp(request -> FakeResponse.json(200, "{\"errors\":[{\"message\":\"Access denied for this resource\"}]}"));
      List<Throwable> errors = new ArrayList<>();
      graphql.client().subscribe("subscription S { a { id } }").subscribe(result -> {}, errors::add);
      org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> errors.size() == 1);
      assertThat(errors.getFirst()).isInstanceOf(GraphQLClientException.class)
          .hasMessage("Access denied for this resource");

      server.onHttp(request -> FakeResponse.json(503, "unavailable"));
      Throwable failure = catchThrowableOfType(java.util.concurrent.ExecutionException.class,
          () -> graphql.client().subscribe("subscription S { a { id } }").nextResult().get(5, TimeUnit.SECONDS))
          .getCause();
      assertThat(failure).isInstanceOf(ServerError.class).hasMessage("Response not successful: Received status code 503");
    }
  }
}
