package com.boostercloud.integration.providerspecific.local.durability;

import static com.boostercloud.integration.providerspecific.local.durability.Documents.variables;

import com.boostercloud.application.tester.Sleep;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLClientOptions;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent command load with an exact record of what the application acknowledged: several workers (virtual
 * threads) send, in a loop, {@code IncrementCounter} mutations on a few shared counters (each with a unique
 * {@code identifier}, which its {@code CounterAdded} event stores, so every stored event can be traced back to the
 * mutation that produced it) and {@code ChangeMultipleCartItems} mutations on fresh carts (one mutation, several
 * {@code CartItemChanged} events: the unit that must never be half applied).
 *
 * <p>A mutation is {@link Outcome#ACKNOWLEDGED} when the application answered {@code true}; one whose request failed
 * (typically because the process was killed while it was in flight) is {@link Outcome#UNKNOWN}: the client cannot
 * know whether it was applied.
 */
final class CommandLoad implements AutoCloseable {

  /** The kind of a mutation. */
  enum Kind {
    /** {@code IncrementCounter}: one {@code CounterAdded} event. */
    INCREMENT_COUNTER,
    /** {@code ChangeMultipleCartItems}: {@code itemsCount} {@code CartItemChanged} events on one cart. */
    CHANGE_MULTIPLE_CART_ITEMS
  }

  /** What the client knows about a mutation. */
  enum Outcome {
    /** Not answered yet. */
    IN_FLIGHT,
    /** The application answered {@code true}. */
    ACKNOWLEDGED,
    /** The request failed: the mutation may or may not have been applied. */
    UNKNOWN
  }

  /**
   * One mutation sent by the load.
   *
   * @param kind its kind
   * @param entityID the counter or cart
   * @param identifier the unique identifier of an increment ({@code null} for carts)
   * @param itemsCount the number of events of a cart mutation (1 for increments)
   * @param outcome what the client knows
   * @param failure the failure of an {@link Outcome#UNKNOWN} mutation
   */
  record Mutation(Kind kind, String entityID, String identifier, int itemsCount, Outcome outcome, String failure) {

    boolean acknowledged() {
      return outcome == Outcome.ACKNOWLEDGED;
    }
  }

  private final GraphQLClient client;
  private final List<String> counterIds;
  private final int cartItemsPerMutation;
  private final double cartMutationRatio;
  private final String label;
  private final Queue<Mutation> mutations = new ConcurrentLinkedQueue<>();
  private final AtomicInteger acknowledged = new AtomicInteger();
  private final AtomicInteger sequence = new AtomicInteger();
  private final AtomicBoolean stopping = new AtomicBoolean();
  private final List<Thread> workers = new ArrayList<>();

  private CommandLoad(ApplicationUnderTest applicationUnderTest, List<String> counterIds, int cartItemsPerMutation,
      double cartMutationRatio, String label) {
    this.client = applicationUnderTest.graphql()
        .withOptions(GraphQLClientOptions.defaults().toBuilder().requestTimeout(Duration.ofSeconds(60)).build())
        .client();
    this.counterIds = List.copyOf(counterIds);
    this.cartItemsPerMutation = cartItemsPerMutation;
    this.cartMutationRatio = cartMutationRatio;
    this.label = Objects.requireNonNull(label, "label");
  }

  /**
   * Starts the workers.
   *
   * @param applicationUnderTest the application
   * @param label a prefix of the identifiers (unique per load, e.g. the cycle)
   * @param counterIds the counters to increment
   * @param workerCount the number of concurrent workers
   * @param cartItemsPerMutation the {@code itemsCount} of the cart mutations
   * @param cartMutationRatio the share of cart mutations (0 to 1)
   * @return the running load
   */
  static CommandLoad start(ApplicationUnderTest applicationUnderTest, String label, List<String> counterIds,
      int workerCount, int cartItemsPerMutation, double cartMutationRatio) {
    CommandLoad load = new CommandLoad(applicationUnderTest, counterIds, cartItemsPerMutation, cartMutationRatio,
        label);
    for (int worker = 0; worker < workerCount; worker++) {
      Random random = new Random(31L * worker + label.hashCode());
      load.workers.add(Thread.ofVirtual().name("durability-load-" + label + "-" + worker)
          .start(() -> load.work(random)));
    }
    return load;
  }

  private void work(Random random) {
    while (!stopping.get()) {
      int number = sequence.incrementAndGet();
      boolean cart = random.nextDouble() < cartMutationRatio;
      String entityID;
      String identifier;
      String document;
      Map<String, Object> variables;
      String field;
      if (cart) {
        entityID = label + "-cart-" + number + "-" + UUID.randomUUID();
        identifier = null;
        document = Documents.CHANGE_MULTIPLE_CART_ITEMS;
        variables = variables("cartId", entityID, "itemsCount", cartItemsPerMutation);
        field = "ChangeMultipleCartItems";
      } else {
        entityID = counterIds.get(random.nextInt(counterIds.size()));
        identifier = label + "-increment-" + number;
        document = Documents.INCREMENT_COUNTER;
        variables = variables("counterId", entityID, "identifier", identifier);
        field = "IncrementCounter";
      }
      Kind kind = cart ? Kind.CHANGE_MULTIPLE_CART_ITEMS : Kind.INCREMENT_COUNTER;
      int events = cart ? cartItemsPerMutation : 1;
      try {
        GraphQLResponse response = client.mutate(document, variables);
        if (Boolean.TRUE.equals(response.get(field))) {
          mutations.add(new Mutation(kind, entityID, identifier, events, Outcome.ACKNOWLEDGED, null));
          acknowledged.incrementAndGet();
        } else {
          mutations.add(new Mutation(kind, entityID, identifier, events, Outcome.UNKNOWN, "answered " + response));
        }
      } catch (RuntimeException e) {
        mutations.add(new Mutation(kind, entityID, identifier, events, Outcome.UNKNOWN, String.valueOf(e)));
        if (stopping.get()) {
          return;
        }
      }
    }
  }

  /**
   * @return how many mutations were acknowledged so far
   */
  int acknowledged() {
    return acknowledged.get();
  }

  /**
   * Stops sending and waits for the in-flight requests to end (they fail fast once the process is gone).
   *
   * @return every mutation sent
   */
  List<Mutation> stop() {
    stopping.set(true);
    for (Thread worker : workers) {
      try {
        if (!worker.join(Duration.ofSeconds(90))) {
          throw new IllegalStateException("The load worker " + worker.getName() + " did not stop");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while stopping the load", e);
      }
    }
    return mutations();
  }

  /**
   * @return the mutations recorded so far
   */
  List<Mutation> mutations() {
    return List.copyOf(mutations);
  }

  /**
   * Blocks until at least {@code count} mutations were acknowledged.
   *
   * @param count the number of acknowledged mutations
   * @param timeout the maximum wait
   */
  void awaitAcknowledged(int count, Duration timeout) {
    Sleep.waitForIt(acknowledged::get, acknowledgedSoFar -> LoadConsistency.accept(acknowledgedSoFar >= count,
        "only " + acknowledgedSoFar + " of " + count + " mutations acknowledged; sent " + mutations.size()
            + ", e.g. " + mutations.stream().filter(mutation -> !mutation.acknowledged()).findFirst().orElse(null)),
        20, timeout.toMillis());
  }

  @Override
  public void close() {
    if (!stopping.get()) {
      stop();
    }
  }
}
