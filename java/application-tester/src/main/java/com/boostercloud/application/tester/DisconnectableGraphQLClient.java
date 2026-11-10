package com.boostercloud.application.tester;

import com.boostercloud.application.tester.graphql.AuthToken;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLClientOptions;
import com.boostercloud.application.tester.graphql.SubscriptionClient;
import com.boostercloud.application.tester.graphql.SubscriptionConnectionException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A {@link GraphQLClient} whose subscriptions go through a websocket: port of {@code DisconnectableApolloClient}
 * ({@code graphql-helper.ts}). Queries and mutations still go over HTTP.
 *
 * <p><b>Always call {@link #disconnect()}</b> (or use try-with-resources) when done: it stops every subscription,
 * sends {@code connection_terminate} and closes the socket; otherwise the socket stays open, as in TypeScript.
 */
public final class DisconnectableGraphQLClient extends GraphQLClient implements AutoCloseable {

  private final SubscriptionClient subscriptionClient;

  DisconnectableGraphQLClient(
      HttpClient httpClient,
      String uri,
      AuthToken authToken,
      GraphQLClientOptions options,
      SubscriptionClient subscriptionClient) {
    super(httpClient, uri, authToken, options, subscriptionClient);
    this.subscriptionClient = subscriptionClient;
  }

  /**
   * @return the underlying {@code subscriptions-transport-ws} client (events, status, active operations)
   */
  public SubscriptionClient websocket() {
    return subscriptionClient;
  }

  /**
   * {@code reconnect()}: closes the socket without stopping the subscriptions ({@code close(false)}), so the client
   * reconnects, re-sends {@code connection_init} and every active {@code start}, and waits until the reconnection is
   * acknowledged.
   *
   * @throws SubscriptionConnectionException when the reconnection is not acknowledged within the connection timeout
   * @throws IllegalStateException when called from a subscription callback of this client (it would deadlock; use
   *     {@link #reconnectAsync()})
   */
  public void reconnect() {
    if (subscriptionClient.isCallbackThread()) {
      throw new IllegalStateException(
          "reconnect() cannot wait inside a subscription callback of the same client; use reconnectAsync()");
    }
    Duration timeout = options().connectionTimeout();
    CompletableFuture<Void> reconnected = reconnectAsync();
    try {
      reconnected.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CancellationException("Interrupted while waiting for the GraphQL websocket to reconnect");
    } catch (TimeoutException e) {
      throw SubscriptionConnectionException.timeout(
          "Timed out after " + timeout + " waiting for the GraphQL websocket " + subscriptionClient.url()
              + " to reconnect");
    } catch (ExecutionException e) {
      throw e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(e.getCause());
    }
  }

  /**
   * The non-blocking form of {@link #reconnect()} ({@code Promise.all(clients.map(c => c.reconnect()))}).
   *
   * @return completes when the reconnection is acknowledged
   */
  public CompletableFuture<Void> reconnectAsync() {
    CompletableFuture<Void> reconnected = new CompletableFuture<>();
    Runnable removeListener = subscriptionClient.onReconnected(payload -> reconnected.complete(null));
    reconnected.whenComplete((ignored, error) -> removeListener.run());
    subscriptionClient.close(false);
    return reconnected;
  }

  /**
   * {@code disconnect()}: {@code subscriptionClient.close()} (stop every subscription, terminate, close the socket,
   * no reconnection) and {@code stop()}.
   */
  public void disconnect() {
    subscriptionClient.close();
    stop();
  }

  /** {@link #disconnect()}. */
  @Override
  public void close() {
    disconnect();
  }
}
