package com.boostercloud.framework.core.services.pubsub;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link Flow.Publisher} over an iterator (the replacement of {@code iterall.createAsyncIterator(array)}): it emits
 * the elements in order as the subscriber requests them and completes when the iterator is exhausted (also without
 * demand, which Reactive Streams allows). Only one subscriber is accepted; requests and cancellation may come from any
 * thread, and re-entrant requests from {@code onNext} do not recurse.
 */
final class IteratorPublisher implements Flow.Publisher<Object> {

  private final Iterator<?> iterator;
  private final AtomicBoolean subscribed = new AtomicBoolean();

  IteratorPublisher(Iterator<?> iterator) {
    this.iterator = Objects.requireNonNull(iterator, "iterator");
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
      subscriber.onError(new IllegalStateException("This publisher accepts a single subscriber"));
      return;
    }
    IteratorSubscription subscription = new IteratorSubscription(subscriber, iterator);
    subscriber.onSubscribe(subscription);
    subscription.drain();
  }

  private static final class IteratorSubscription implements Flow.Subscription {
    private final Flow.Subscriber<? super Object> subscriber;
    private final Iterator<?> iterator;
    private final AtomicLong demand = new AtomicLong();
    private final AtomicInteger workInProgress = new AtomicInteger();
    private volatile boolean cancelled;
    private boolean done;

    IteratorSubscription(Flow.Subscriber<? super Object> subscriber, Iterator<?> iterator) {
      this.subscriber = subscriber;
      this.iterator = iterator;
    }

    @Override
    public void request(long n) {
      if (n <= 0) {
        cancelled = true;
        subscriber.onError(new IllegalArgumentException("Reactive Streams §3.9: the requested amount must be positive, got " + n));
        return;
      }
      demand.accumulateAndGet(n, (current, added) -> current + added < 0 ? Long.MAX_VALUE : current + added);
      drain();
    }

    @Override
    public void cancel() {
      cancelled = true;
    }

    void drain() {
      if (workInProgress.getAndIncrement() != 0) {
        return;
      }
      do {
        while (!cancelled && !done) {
          boolean hasNext;
          Object next = null;
          try {
            hasNext = iterator.hasNext();
            if (hasNext && demand.get() > 0) {
              next = iterator.next();
            }
          } catch (RuntimeException e) {
            done = true;
            subscriber.onError(e);
            break;
          }
          if (!hasNext) {
            done = true;
            subscriber.onComplete();
            break;
          }
          if (demand.get() == 0) {
            break;
          }
          demand.decrementAndGet();
          subscriber.onNext(next);
        }
      } while (workInProgress.decrementAndGet() != 0);
    }
  }
}
