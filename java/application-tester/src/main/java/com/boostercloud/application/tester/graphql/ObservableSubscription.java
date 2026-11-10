package com.boostercloud.application.tester.graphql;

/**
 * The handle returned by {@link SubscriptionObservable#subscribe(SubscriptionObserver)}: Apollo's
 * {@code ObservableSubscription}.
 */
public interface ObservableSubscription {

  /**
   * Stops delivering to this observer. When it was the last observer of the observable, the GraphQL operation is
   * stopped too (a {@code stop} message is sent shortly after). Idempotent.
   */
  void unsubscribe();

  /**
   * @return whether this observer receives nothing more (unsubscribed, failed or completed)
   */
  boolean isClosed();
}
