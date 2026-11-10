package com.boostercloud.framework.core.services.graphql;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link Flow.Publisher} over an {@link Iterator}: the Java form of the TypeScript async iterator a subscription
 * resolver returns ({@code pubSub.asyncIterator(...)}). graphql-java's subscription execution requires the
 * subscription resolver to return a publisher; it maps every published item (a read model) through the selection set.
 *
 * <p>Items are emitted on the thread that requests them, honouring the requested demand, and the publisher completes
 * when the iterator is exhausted (an empty iterator completes immediately). An exception thrown by the iterator is
 * signalled with {@code onError}. The iterator can be consumed once: a second subscriber receives an
 * {@link IllegalStateException}.
 */
public final class IteratorPublisher implements Flow.Publisher<Object> {

  private final Iterator<?> iterator;
  private final AtomicBoolean subscribed = new AtomicBoolean();

  /**
   * @param iterator the source of the items
   */
  public IteratorPublisher(Iterator<?> iterator) {
    this.iterator = Objects.requireNonNull(iterator, "iterator");
  }

  /**
   * @param iterator the source of the items, or {@code null}
   * @return the publisher, or {@code null} for a {@code null} iterator
   */
  public static IteratorPublisher of(Iterator<?> iterator) {
    return iterator == null ? null : new IteratorPublisher(iterator);
  }

  /**
   * @return the iterator this publisher emits
   */
  public Iterator<?> iterator() {
    return iterator;
  }

  @Override
  public void subscribe(Flow.Subscriber<? super Object> subscriber) {
    Objects.requireNonNull(subscriber, "subscriber");
    if (!subscribed.compareAndSet(false, true)) {
      subscriber.onSubscribe(new Flow.Subscription() {
        @Override
        public void request(long n) {}

        @Override
        public void cancel() {}
      });
      subscriber.onError(new IllegalStateException("This subscription can only be consumed once"));
      return;
    }
    subscriber.onSubscribe(new IteratorSubscription(subscriber));
  }

  private final class IteratorSubscription implements Flow.Subscription {
    private final Flow.Subscriber<? super Object> subscriber;
    private final AtomicLong demand = new AtomicLong();
    private final AtomicInteger wip = new AtomicInteger();
    private volatile boolean done;

    private IteratorSubscription(Flow.Subscriber<? super Object> subscriber) {
      this.subscriber = subscriber;
    }

    @Override
    public void request(long n) {
      if (done) {
        return;
      }
      if (n <= 0) {
        done = true;
        subscriber.onError(new IllegalArgumentException("Non-positive subscription request: " + n));
        return;
      }
      demand.accumulateAndGet(n, (current, added) -> {
        long sum = current + added;
        return sum < 0 ? Long.MAX_VALUE : sum;
      });
      drain();
    }

    @Override
    public void cancel() {
      done = true;
    }

    private void drain() {
      if (wip.getAndIncrement() != 0) {
        return;
      }
      int missed = 1;
      do {
        while (!done && demand.get() > 0) {
          boolean hasNext;
          Object next;
          try {
            hasNext = iterator.hasNext();
            next = hasNext ? iterator.next() : null;
          } catch (RuntimeException e) {
            done = true;
            subscriber.onError(e);
            return;
          }
          if (!hasNext) {
            done = true;
            subscriber.onComplete();
            return;
          }
          demand.decrementAndGet();
          subscriber.onNext(next);
        }
        if (!done) {
          try {
            if (!iterator.hasNext()) {
              done = true;
              subscriber.onComplete();
              return;
            }
          } catch (RuntimeException e) {
            done = true;
            subscriber.onError(e);
            return;
          }
        }
        missed = wip.addAndGet(-missed);
      } while (missed != 0);
    }
  }
}
