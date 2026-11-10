package com.boostercloud.framework.core.services.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class IteratorPublisherTest {

  /** Subscribes, requests everything and returns the items once the publisher completes. */
  static List<Object> collect(Flow.Publisher<Object> publisher) {
    Recorder recorder = new Recorder(Long.MAX_VALUE);
    publisher.subscribe(recorder);
    assertThat(recorder.completed).isTrue();
    return recorder.items;
  }

  @Test
  void emitsTheItemsAndCompletes() {
    assertThat(collect(new IteratorPublisher(List.of("a", "b", "c").iterator()))).containsExactly("a", "b", "c");
  }

  @Test
  void completesOnTheFirstRequestForAnEmptyIterator() {
    Recorder recorder = new Recorder(1);

    new IteratorPublisher(List.of().iterator()).subscribe(recorder);

    assertThat(recorder.items).isEmpty();
    assertThat(recorder.completed).isTrue();
  }

  @Test
  void honoursTheDemand() {
    Recorder recorder = new Recorder(1);
    new IteratorPublisher(List.of("a", "b", "c").iterator()).subscribe(recorder);

    assertThat(recorder.items).containsExactly("a");
    assertThat(recorder.completed).isFalse();

    recorder.subscription.request(2);

    assertThat(recorder.items).containsExactly("a", "b", "c");
    assertThat(recorder.completed).isTrue();
  }

  @Test
  void stopsWhenCancelled() {
    Recorder recorder = new Recorder(1);
    new IteratorPublisher(List.of("a", "b").iterator()).subscribe(recorder);

    recorder.subscription.cancel();
    recorder.subscription.request(5);

    assertThat(recorder.items).containsExactly("a");
    assertThat(recorder.completed).isFalse();
  }

  @Test
  void signalsTheErrorsOfTheIterator() {
    Iterator<Object> failing = new Iterator<>() {
      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public Object next() {
        throw new IllegalStateException("broken");
      }
    };
    Recorder recorder = new Recorder(Long.MAX_VALUE);

    new IteratorPublisher(failing).subscribe(recorder);

    assertThat(recorder.error.get()).hasMessage("broken");
  }

  @Test
  void canBeConsumedOnce() {
    IteratorPublisher publisher = new IteratorPublisher(List.of("a").iterator());
    collect(publisher);
    Recorder second = new Recorder(1);

    publisher.subscribe(second);

    assertThat(second.error.get()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsNonPositiveRequests() {
    Recorder recorder = new Recorder(0);
    new IteratorPublisher(List.of("a").iterator()).subscribe(recorder);

    recorder.subscription.request(0);

    assertThat(recorder.error.get()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void hasNoPublisherForANullIterator() {
    assertThat(IteratorPublisher.of(null)).isNull();
  }

  private static final class Recorder implements Flow.Subscriber<Object> {
    private final long initialDemand;
    private final List<Object> items = new ArrayList<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicBoolean done = new AtomicBoolean();
    private boolean completed;
    private Flow.Subscription subscription;

    private Recorder(long initialDemand) {
      this.initialDemand = initialDemand;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      if (initialDemand > 0) {
        subscription.request(initialDemand);
      }
    }

    @Override
    public void onNext(Object item) {
      items.add(item);
    }

    @Override
    public void onError(Throwable throwable) {
      error.set(throwable);
      done.set(true);
    }

    @Override
    public void onComplete() {
      completed = true;
      done.set(true);
    }
  }
}
