package com.boostercloud.application.tester.graphql;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The results of {@link GraphQLClient#subscribe(String, java.util.Map)}: Apollo's {@code Observable<FetchResult>}.
 *
 * <p>As in Apollo Client 3.7 the operation starts when the observable is created (one server-side subscription per
 * {@code client.subscribe(...)} call) and every {@link #subscribe(SubscriptionObserver)} attaches another observer to
 * it. A new observer immediately receives the latest result (or the error), so a result that arrived before the
 * observer is not lost. When the last observer unsubscribes the operation is stopped ({@code stop}) for good.
 *
 * <pre>{@code
 * SubscriptionObservable cart = client.subscribe(CART_SUBSCRIPTION, Map.of("cartId", cartId));
 * cart.subscribe(result -> {});                 // keep it alive
 * client.mutate(CHANGE_CART, Map.of("cartId", cartId, "productId", productId));
 * GraphQLResponse next = cart.nextResult().get(30, TimeUnit.SECONDS);   // promisifyNextSubscriptionResult
 * }</pre>
 */
public final class SubscriptionObservable {

  private final Concast concast;

  SubscriptionObservable(Concast concast) {
    this.concast = concast;
  }

  /**
   * @param observer receives the results
   * @return the handle that detaches the observer
   */
  public ObservableSubscription subscribe(SubscriptionObserver observer) {
    Objects.requireNonNull(observer, "observer");
    Concast.ObserverSubscription subscription = new Concast.ObserverSubscription(observer, concast);
    concast.loop().run(() -> concast.addObserver(subscription));
    return subscription;
  }

  /**
   * {@code subscribe(next)}; errors are reported to stderr (zen-observable rethrows unhandled errors).
   *
   * @param next receives the results
   * @return the handle that detaches the observer
   */
  public ObservableSubscription subscribe(Consumer<GraphQLResponse> next) {
    Objects.requireNonNull(next, "next");
    return subscribe(new SubscriptionObserver() {
      @Override
      public void next(GraphQLResponse result) {
        next.accept(result);
      }
    });
  }

  /**
   * {@code subscribe(next, error)}.
   *
   * @param next receives the results
   * @param error receives the failure
   * @return the handle that detaches the observer
   */
  public ObservableSubscription subscribe(Consumer<GraphQLResponse> next, Consumer<Throwable> error) {
    return subscribe(next, error, () -> {});
  }

  /**
   * {@code subscribe(next, error, complete)}.
   *
   * @param next receives the results
   * @param error receives the failure
   * @param complete called on completion
   * @return the handle that detaches the observer
   */
  public ObservableSubscription subscribe(
      Consumer<GraphQLResponse> next, Consumer<Throwable> error, Runnable complete) {
    Objects.requireNonNull(next, "next");
    Objects.requireNonNull(error, "error");
    Objects.requireNonNull(complete, "complete");
    return subscribe(new SubscriptionObserver() {
      @Override
      public void next(GraphQLResponse result) {
        next.accept(result);
      }

      @Override
      public void error(Throwable failure) {
        error.accept(failure);
      }

      @Override
      public void complete() {
        complete.run();
      }
    });
  }

  /**
   * The {@code promisifyNextSubscriptionResult} helper of the TypeScript suites: attaches an observer and completes
   * with the next result it receives (the latest one at once, if any) or fails with its error. Like the TypeScript
   * helper, the observer stays attached afterwards (so the operation stays active).
   *
   * @return the next result
   */
  public CompletableFuture<GraphQLResponse> nextResult() {
    CompletableFuture<GraphQLResponse> result = new CompletableFuture<>();
    subscribe(new SubscriptionObserver() {
      @Override
      public void next(GraphQLResponse response) {
        result.complete(response);
      }

      @Override
      public void error(Throwable error) {
        result.completeExceptionally(error);
      }
    });
    return result;
  }
}
