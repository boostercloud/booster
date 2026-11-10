package com.boostercloud.application.tester.graphql;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A GraphQL test client: what the TypeScript {@code GraphQLHelper.client()} builds with Apollo Client (an auth link,
 * an {@code HttpLink} over {@code cross-fetch} and an {@code InMemoryCache} with the {@code no-cache} query policy),
 * reduced to its observable behaviour:
 *
 * <ul>
 *   <li>Documents are parsed and checked like Apollo's {@code gql}/{@code checkDocument}; by default
 *       {@code __typename} is added to every selection set except the operation's root (see
 *       {@link GraphQLDocument#withTypename()}), so responses carry {@code __typename} keys like in TypeScript.
 *   <li>Variables are merged over the document's default values; over HTTP the variables the document does not use
 *       are stripped.
 *   <li>Every operation hits the network (nothing is cached). The token, if any, is resolved per operation.
 *   <li>{@link #query}/{@link #mutate} block and return the result; with the default {@link ErrorPolicy#NONE} a
 *       response with {@code errors} throws {@link GraphQLClientException} (Apollo's {@code ApolloError}) carrying
 *       the raw errors, and transport failures throw it with the {@link GraphQLClientException#networkError()}.
 *   <li>{@link #subscribe} returns a {@link SubscriptionObservable}. On a plain client it is a single HTTP request;
 *       on a client with subscriptions ({@code DisconnectableGraphQLClient}) every document whose main definition
 *       is a {@code subscription} goes through the websocket (Apollo's split link), including one passed to
 *       {@code query}/{@code mutate} (which then wait for the server's {@code complete}).
 * </ul>
 *
 * Instances are thread-safe; each call is independent.
 */
public class GraphQLClient {

  private final HttpLink httpLink;
  private final GraphQLClientOptions options;
  private final SubscriptionClient subscriptionClient;
  private final String uri;

  /**
   * A client over HTTP only.
   *
   * @param httpClient the HTTP client (shared, HTTP/1.1 recommended)
   * @param uri the GraphQL endpoint
   * @param authToken the token
   * @param options the options
   */
  public GraphQLClient(HttpClient httpClient, String uri, AuthToken authToken, GraphQLClientOptions options) {
    this(httpClient, uri, authToken, options, null);
  }

  /**
   * A client that routes subscriptions through a websocket.
   *
   * @param httpClient the HTTP client
   * @param uri the GraphQL endpoint
   * @param authToken the token
   * @param options the options
   * @param subscriptionClient the websocket client, or {@code null} for HTTP only
   */
  protected GraphQLClient(
      HttpClient httpClient,
      String uri,
      AuthToken authToken,
      GraphQLClientOptions options,
      SubscriptionClient subscriptionClient) {
    this.uri = uri;
    this.options = options == null ? GraphQLClientOptions.defaults() : options;
    this.httpLink = new HttpLink(httpClient, uri, authToken == null ? AuthToken.none() : authToken, this.options);
    this.subscriptionClient = subscriptionClient;
  }

  /**
   * @return the GraphQL HTTP endpoint
   */
  public String uri() {
    return uri;
  }

  /**
   * @return the options
   */
  public GraphQLClientOptions options() {
    return options;
  }

  /**
   * {@code client.query({ query })}.
   *
   * @param document the GraphQL document
   * @return the response
   * @throws GraphQLClientException when the response has errors or the request fails
   * @throws IllegalArgumentException for invalid documents
   */
  public GraphQLResponse query(String document) {
    return query(document, null, ErrorPolicy.NONE);
  }

  /**
   * {@code client.query({ query, variables })}.
   *
   * @param document the GraphQL document
   * @param variables the variables ({@code null} values allowed), may be {@code null}
   * @return the response
   * @throws GraphQLClientException when the response has errors or the request fails
   * @throws IllegalArgumentException for invalid documents
   */
  public GraphQLResponse query(String document, Map<String, ?> variables) {
    return query(document, variables, ErrorPolicy.NONE);
  }

  /**
   * {@code client.query({ query, variables, errorPolicy })}.
   *
   * @param document the GraphQL document
   * @param variables the variables, may be {@code null}
   * @param errorPolicy what to do with GraphQL errors
   * @return the response
   * @throws GraphQLClientException when the request fails, or the response has errors and the policy is
   *     {@link ErrorPolicy#NONE}
   * @throws IllegalArgumentException for invalid documents
   */
  public GraphQLResponse query(String document, Map<String, ?> variables, ErrorPolicy errorPolicy) {
    return execute(document, variables, errorPolicy);
  }

  /**
   * {@code client.mutate({ mutation })}. Any document is accepted (a query document is simply posted).
   *
   * @param document the GraphQL document
   * @return the response
   * @throws GraphQLClientException when the response has errors or the request fails
   */
  public GraphQLResponse mutate(String document) {
    return mutate(document, null, ErrorPolicy.NONE);
  }

  /**
   * {@code client.mutate({ mutation, variables })}.
   *
   * @param document the GraphQL document
   * @param variables the variables, may be {@code null}
   * @return the response
   * @throws GraphQLClientException when the response has errors or the request fails
   */
  public GraphQLResponse mutate(String document, Map<String, ?> variables) {
    return mutate(document, variables, ErrorPolicy.NONE);
  }

  /**
   * {@code client.mutate({ mutation, variables, errorPolicy })}.
   *
   * @param document the GraphQL document
   * @param variables the variables, may be {@code null}
   * @param errorPolicy what to do with GraphQL errors
   * @return the response
   * @throws GraphQLClientException when the request fails, or the response has errors and the policy is
   *     {@link ErrorPolicy#NONE}
   */
  public GraphQLResponse mutate(String document, Map<String, ?> variables, ErrorPolicy errorPolicy) {
    return execute(document, variables, errorPolicy);
  }

  /**
   * {@code client.subscribe({ query })}.
   *
   * @param document the GraphQL document
   * @return the started subscription
   * @throws IllegalArgumentException for invalid documents
   */
  public SubscriptionObservable subscribe(String document) {
    return subscribe(document, null);
  }

  /**
   * {@code client.subscribe({ query, variables })}: starts the operation now and returns its multicast results.
   *
   * @param document the GraphQL document
   * @param variables the variables (all of them are sent over the websocket), may be {@code null}
   * @return the started subscription
   * @throws IllegalArgumentException for invalid documents
   */
  public SubscriptionObservable subscribe(String document, Map<String, ?> variables) {
    Operation operation = prepare(document, variables);
    if (subscriptionClient != null && operation.subscription()) {
      SubscriptionClient websocket = subscriptionClient;
      return new SubscriptionObservable(
          new Concast(websocket.loop(), sink -> websocket.startOnLoop(operation, sink)));
    }
    EventLoop loop = new EventLoop("graphql-http-subscription");
    return new SubscriptionObservable(new Concast(loop, sink -> startOverHttp(loop, operation, sink)));
  }

  /**
   * Prepares an operation the way this client sends it (parsed, checked, {@code __typename} added unless disabled,
   * variables merged over the defaults). Useful to see what goes on the wire.
   *
   * @param document the GraphQL document
   * @param variables the variables, may be {@code null}
   * @return the operation
   */
  public Operation prepare(String document, Map<String, ?> variables) {
    return Operation.prepare(document, variables, options.addTypename());
  }

  /** Apollo's {@code client.stop()}: nothing to stop, every call of this client is independent. */
  public void stop() {}

  /**
   * @return the websocket client, or {@code null} for an HTTP-only client
   */
  protected SubscriptionClient subscriptionClient() {
    return subscriptionClient;
  }

  private GraphQLResponse execute(String document, Map<String, ?> variables, ErrorPolicy errorPolicy) {
    ErrorPolicy policy = errorPolicy == null ? ErrorPolicy.NONE : errorPolicy;
    Operation operation = prepare(document, variables);
    if (subscriptionClient != null && operation.subscription()) {
      return executeOverWebSocket(operation, policy);
    }
    Object raw;
    try {
      raw = httpLink.execute(operation);
    } catch (ServerError error) {
      // HttpLink forwards a result that has both errors and data before failing (Apollo's errorPolicy then applies)
      if (error.result() instanceof Map<?, ?> result && Js.truthy(result.get("errors")) && Js.truthy(result.get("data"))) {
        GraphQLResponse partial = GraphQLResponse.fromResult(result);
        if (partial.hasErrors() && policy == ErrorPolicy.NONE) {
          throw GraphQLClientException.ofGraphQLErrors(partial.errors());
        }
      }
      throw GraphQLClientException.ofNetworkError(error);
    } catch (ServerParseError | FetchError error) {
      throw GraphQLClientException.ofNetworkError(error);
    }
    return applyErrorPolicy(GraphQLResponse.fromResult(raw), policy);
  }

  private static GraphQLResponse applyErrorPolicy(GraphQLResponse response, ErrorPolicy policy) {
    if (response.hasErrors() && policy == ErrorPolicy.NONE) {
      throw GraphQLClientException.ofGraphQLErrors(response.errors());
    }
    return policy == ErrorPolicy.IGNORE ? response.withoutErrors() : response;
  }

  /** A query/mutation whose document is a subscription: the result of the websocket operation once complete. */
  private GraphQLResponse executeOverWebSocket(Operation operation, ErrorPolicy policy) {
    if (subscriptionClient.isCallbackThread()) {
      throw new IllegalStateException(
          "A subscription document cannot be queried from a subscription callback of the same client");
    }
    CompletableFuture<GraphQLResponse> done = new CompletableFuture<>();
    Runnable[] stop = new Runnable[1];
    subscriptionClient.loop().run(() -> stop[0] = subscriptionClient.startOnLoop(operation, new OperationObserver() {
      private GraphQLResponse latest;

      @Override
      public void next(Map<String, Object> result) {
        GraphQLResponse response = GraphQLResponse.fromResult(result);
        if (response.hasErrors() && policy == ErrorPolicy.NONE) {
          done.completeExceptionally(GraphQLClientException.ofGraphQLErrors(response.errors()));
          subscriptionClient.loop().execute(stop[0]);
        } else {
          latest = policy == ErrorPolicy.IGNORE ? response.withoutErrors() : response;
        }
      }

      @Override
      public void error(Throwable error) {
        done.completeExceptionally(
            error instanceof GraphQLClientException clientError ? clientError : GraphQLClientException.ofNetworkError(error));
      }

      @Override
      public void complete() {
        done.complete(latest == null ? new GraphQLResponse(null, null, null) : latest);
      }
    }));
    Duration timeout = options.requestTimeout();
    try {
      return timeout == null ? done.get() : done.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      subscriptionClient.loop().execute(stop[0]);
      throw new java.util.concurrent.CancellationException("Interrupted while waiting for the GraphQL operation");
    } catch (TimeoutException e) {
      subscriptionClient.loop().execute(stop[0]);
      throw GraphQLClientException.ofNetworkError(
          new TimeoutException("Timed out after " + timeout + " waiting for the GraphQL operation to complete"));
    } catch (ExecutionException e) {
      throw EventLoop.rethrow(e.getCause());
    }
  }

  /** The HTTP link as an observable source: one result (or failure) then completion; cancellable. */
  private Runnable startOverHttp(EventLoop loop, Operation operation, OperationObserver sink) {
    HttpLink.Pending pending = httpLink.request(operation);
    boolean[] cancelled = new boolean[1];
    pending.result().whenComplete((raw, failure) -> loop.execute(() -> {
      if (cancelled[0]) {
        return;
      }
      if (failure == null) {
        sink.next(asResultMap(raw));
        sink.complete();
        return;
      }
      Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
          ? failure.getCause()
          : failure;
      if (cause instanceof ServerError serverError && serverError.result() instanceof Map<?, ?> result
          && Js.truthy(result.get("errors")) && Js.truthy(result.get("data"))) {
        sink.next(asResultMap(result));
      }
      sink.error(cause);
    }));
    return () -> {
      cancelled[0] = true;
      pending.cancel();
    };
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asResultMap(Object raw) {
    return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + Objects.toString(uri) + "]";
  }
}
