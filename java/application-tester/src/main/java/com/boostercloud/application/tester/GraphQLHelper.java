package com.boostercloud.application.tester;

import com.boostercloud.application.tester.graphql.AuthToken;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLClientOptions;
import com.boostercloud.application.tester.graphql.SubscriptionClient;
import com.boostercloud.application.tester.graphql.SubscriptionClientOptions;
import com.boostercloud.application.tester.graphql.SubscriptionConnectionException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Builds GraphQL clients for the application under test. Port of {@code GraphQLHelper} ({@code graphql-helper.ts}).
 *
 * <ul>
 *   <li>{@link #client()} / {@link #client(String)} / {@link #client(Supplier)}: HTTP client on
 *       {@code outputs().graphqlURL()}, sending {@code Authorization: Bearer <token>} when there is a token (a supplier
 *       is evaluated on every operation, an empty token sends nothing). Each call builds a new client.
 *   <li>{@link #clientWithSubscriptions()} (and overloads): additionally opens a websocket to
 *       {@code outputs().websocketURL()} (sub-protocol {@code graphql-ws}, reconnecting, {@code connection_init}
 *       payload {@code {"Authorization": "Bearer <token>"}} or {@code {}}, re-evaluated on every reconnection) and
 *       blocks until the server acknowledges it. A {@code connection_error} throws
 *       {@link SubscriptionConnectionException} with the server's payload.
 * </ul>
 */
public final class GraphQLHelper {

  private final ProviderTestHelper providerTestHelper;
  private final GraphQLClientOptions options;
  private volatile HttpClient httpClient;

  /**
   * @param providerTestHelper the provider's test helper
   */
  public GraphQLHelper(ProviderTestHelper providerTestHelper) {
    this(providerTestHelper, GraphQLClientOptions.defaults());
  }

  /**
   * @param providerTestHelper the provider's test helper
   * @param options the client options
   */
  public GraphQLHelper(ProviderTestHelper providerTestHelper, GraphQLClientOptions options) {
    this.providerTestHelper = Objects.requireNonNull(providerTestHelper, "providerTestHelper");
    this.options = options == null ? GraphQLClientOptions.defaults() : options;
  }

  /**
   * @return the client options
   */
  public GraphQLClientOptions options() {
    return options;
  }

  /**
   * @param newOptions other client options
   * @return a helper for the same application with those options
   */
  public GraphQLHelper withOptions(GraphQLClientOptions newOptions) {
    return new GraphQLHelper(providerTestHelper, newOptions);
  }

  /**
   * @return an anonymous HTTP client
   */
  public GraphQLClient client() {
    return client(AuthToken.none());
  }

  /**
   * @param authToken the token ({@code null} or empty for an anonymous client)
   * @return an HTTP client authenticated with the token
   */
  public GraphQLClient client(String authToken) {
    return client(AuthToken.of(authToken));
  }

  /**
   * @param authToken evaluated for every operation
   * @return an HTTP client authenticated with the supplied tokens
   */
  public GraphQLClient client(Supplier<String> authToken) {
    return client(AuthToken.of(authToken));
  }

  /**
   * @param authToken the token
   * @return an HTTP client
   */
  public GraphQLClient client(AuthToken authToken) {
    return new GraphQLClient(httpClient(), providerTestHelper.outputs().graphqlURL(), authToken, options);
  }

  /**
   * @return an anonymous client with subscriptions, connected
   * @throws SubscriptionConnectionException when the connection is refused or not acknowledged in time
   */
  public DisconnectableGraphQLClient clientWithSubscriptions() {
    return clientWithSubscriptions(AuthToken.none());
  }

  /**
   * @param authToken the token ({@code null} or empty for an anonymous client)
   * @return a client with subscriptions, connected
   * @throws SubscriptionConnectionException when the connection is refused or not acknowledged in time
   */
  public DisconnectableGraphQLClient clientWithSubscriptions(String authToken) {
    return clientWithSubscriptions(AuthToken.of(authToken));
  }

  /**
   * @param authToken evaluated for every HTTP operation and every websocket (re)connection
   * @return a client with subscriptions, connected
   * @throws SubscriptionConnectionException when the connection is refused or not acknowledged in time
   */
  public DisconnectableGraphQLClient clientWithSubscriptions(Supplier<String> authToken) {
    return clientWithSubscriptions(AuthToken.of(authToken));
  }

  /**
   * @param authToken the token
   * @return a client with subscriptions, connected
   * @throws SubscriptionConnectionException when the connection is refused (then the websocket is closed) or not
   *     acknowledged within {@link GraphQLClientOptions#connectionTimeout()}
   */
  public DisconnectableGraphQLClient clientWithSubscriptions(AuthToken authToken) {
    AuthToken token = authToken == null ? AuthToken.none() : authToken;
    CompletableFuture<Void> connected = new CompletableFuture<>();
    SubscriptionClientOptions websocketOptions = SubscriptionClientOptions.builder()
        .reconnect(true)
        .connectionParams(() -> {
          Map<String, Object> params = new LinkedHashMap<>();
          token.authorization().ifPresent(authorization -> params.put("Authorization", authorization));
          return params;
        })
        .connectionCallback(error -> {
          if (Sleep.isTruthy(error)) {
            connected.completeExceptionally(SubscriptionConnectionException.connectionError(error));
            return;
          }
          connected.complete(null);
        })
        .socketLogger(options.socketLogger())
        .build();
    String websocketURL = providerTestHelper.outputs().websocketURL();
    SubscriptionClient subscriptionClient = new SubscriptionClient(httpClient(), websocketURL, websocketOptions);
    Duration timeout = options.connectionTimeout();
    try {
      connected.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      subscriptionClient.close();
      Thread.currentThread().interrupt();
      throw new CancellationException("Interrupted while connecting to " + websocketURL);
    } catch (TimeoutException e) {
      subscriptionClient.close();
      throw SubscriptionConnectionException.timeout(
          "Timed out after " + timeout + " waiting for the GraphQL websocket " + websocketURL
              + " to acknowledge the connection");
    } catch (ExecutionException e) {
      subscriptionClient.close();
      throw e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(e.getCause());
    }
    return new DisconnectableGraphQLClient(
        httpClient(), providerTestHelper.outputs().graphqlURL(), token, options, subscriptionClient);
  }

  private HttpClient httpClient() {
    HttpClient client = httpClient;
    if (client == null) {
      synchronized (this) {
        client = httpClient;
        if (client == null) {
          client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
          httpClient = client;
        }
      }
    }
    return client;
  }
}
