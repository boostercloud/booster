package com.boostercloud.application.tester.graphql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The multicasting observable behind {@link SubscriptionObservable}: a port of Apollo Client's {@code Concast} (one
 * source) combined with the per-observer result mapping of {@code QueryManager.startGraphQLSubscription}.
 *
 * <ul>
 *   <li>The source (the GraphQL operation) is started eagerly, when the observable is created, and shared by every
 *       observer.
 *   <li>A new observer immediately receives the latest message (the last result, or the error); if the source has
 *       completed after a result, it then receives {@code complete}.
 *   <li>A result with GraphQL errors fails only the observer that receives it (with an {@code ApolloError}), which
 *       then leaves; when the last observer leaves (unsubscribes or fails), the Concast completes and the source is
 *       stopped.
 *   <li>On error or completion the source is unsubscribed on the next loop turn (Apollo's {@code setTimeout}), so the
 *       library's own bookkeeping of the operation finishes first.
 * </ul>
 *
 * All methods run on the owning event loop.
 */
final class Concast implements OperationObserver {

  private final EventLoop loop;
  private final Set<ObserverSubscription> observers = new LinkedHashSet<>();
  private Runnable sourceUnsubscribe;
  private boolean closed;
  private Map<String, Object> latestNext;
  private Throwable latestError;

  /**
   * @param loop the event loop every method runs on
   * @param source starts the operation with the given sink and returns the function that stops it
   */
  Concast(EventLoop loop, Function<OperationObserver, Runnable> source) {
    this.loop = loop;
    loop.run(() -> {
      Runnable unsubscribe = source.apply(this);
      if (closed) {
        if (unsubscribe != null) {
          loop.execute(unsubscribe);
        }
      } else {
        sourceUnsubscribe = unsubscribe;
      }
    });
  }

  EventLoop loop() {
    return loop;
  }

  @Override
  public void next(Map<String, Object> result) {
    if (closed) {
      return;
    }
    latestNext = result;
    latestError = null;
    for (ObserverSubscription observer : snapshot()) {
      observer.deliverNext(result);
    }
  }

  @Override
  public void error(Throwable error) {
    if (closed) {
      return;
    }
    stopSource();
    latestNext = null;
    latestError = error;
    for (ObserverSubscription observer : snapshot()) {
      observer.deliverError(error);
    }
  }

  @Override
  public void complete() {
    if (closed) {
      return;
    }
    stopSource();
    for (ObserverSubscription observer : snapshot()) {
      observer.deliverComplete();
    }
  }

  void addObserver(ObserverSubscription observer) {
    if (observers.contains(observer)) {
      return;
    }
    deliverLastMessage(observer);
    observers.add(observer);
    if (observer.isClosed()) {
      // zen-observable runs the cleanup of an observer closed while subscribing
      removeObserver(observer);
    }
  }

  void removeObserver(ObserverSubscription observer) {
    if (observers.remove(observer) && observers.isEmpty()) {
      complete();
    }
  }

  private void deliverLastMessage(ObserverSubscription observer) {
    if (latestError != null) {
      observer.deliverError(latestError);
    } else if (latestNext != null) {
      observer.deliverNext(latestNext);
      if (closed) {
        observer.deliverComplete();
      }
    }
  }

  private void stopSource() {
    Runnable unsubscribe = sourceUnsubscribe;
    sourceUnsubscribe = null;
    closed = true;
    if (unsubscribe != null) {
      loop.execute(unsubscribe);
    }
  }

  private List<ObserverSubscription> snapshot() {
    return new ArrayList<>(observers);
  }

  /** One observer of the Concast (a zen-observable {@code SubscriptionObserver} with Apollo's result mapper). */
  static final class ObserverSubscription implements ObservableSubscription {
    private final SubscriptionObserver observer;
    private final Concast concast;
    private volatile boolean closed;

    ObserverSubscription(SubscriptionObserver observer, Concast concast) {
      this.observer = observer;
      this.concast = concast;
    }

    void deliverNext(Map<String, Object> result) {
      if (closed) {
        return;
      }
      GraphQLResponse response = GraphQLResponse.fromResult(result);
      if (response.hasErrors()) {
        deliverError(GraphQLClientException.ofGraphQLErrors(response.errors()));
        return;
      }
      try {
        observer.next(response);
      } catch (RuntimeException error) {
        EventLoop.reportUncaught(error);
      }
    }

    void deliverError(Throwable error) {
      if (closed) {
        return;
      }
      closed = true;
      try {
        observer.error(error);
      } catch (RuntimeException failure) {
        EventLoop.reportUncaught(failure);
      }
      concast.removeObserver(this);
    }

    void deliverComplete() {
      if (closed) {
        return;
      }
      closed = true;
      try {
        observer.complete();
      } catch (RuntimeException failure) {
        EventLoop.reportUncaught(failure);
      }
      concast.removeObserver(this);
    }

    @Override
    public void unsubscribe() {
      concast.loop.run(() -> {
        if (!closed) {
          closed = true;
          concast.removeObserver(this);
        }
      });
    }

    @Override
    public boolean isClosed() {
      return closed;
    }
  }
}
