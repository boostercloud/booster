package com.boostercloud.integration.providerspecific.local.durability;

import static com.boostercloud.integration.providerspecific.local.durability.Documents.variables;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.Sleep;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.providerspecific.local.durability.CommandLoad.Kind;
import com.boostercloud.integration.providerspecific.local.durability.CommandLoad.Mutation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The consistency rules a crash must not break, checked against the data files and the GraphQL API after the
 * application restarted:
 *
 * <ol>
 *   <li><b>acknowledged means stored</b>: every mutation the application acknowledged has all its events in the event
 *       store, exactly once;</li>
 *   <li><b>all or nothing</b>: a mutation the client could not get an answer for has all its events stored, or none
 *       (never part of a {@code ChangeMultipleCartItems} batch), and never twice;</li>
 *   <li><b>nothing invented</b>: every stored event of the load comes from a mutation it sent;</li>
 *   <li><b>convergence</b>: each read model reaches the state implied by the stored events (a counter's
 *       {@code amount} is the number of its {@code CounterAdded} events, a cart has the items of its stored
 *       {@code CartItemChanged} events, a cart without events has no read model). The one exception is the
 *       at-most-once window of the framework: an event whose processing had started when the process died
 *       (reported by the recovery, see {@link RecoveryLog}) is not processed again, so the read model of its entity
 *       may miss it until the entity's next event; it is never partially applied;</li>
 *   <li><b>no double projection</b>: an {@code amount} never exceeds the number of stored events, and the read model
 *       was not stored more often ({@code boosterMetadata.version}) than there are events to project.</li>
 * </ol>
 */
final class LoadConsistency {

  /** How long the read models may take to converge after a restart. */
  static final long CONVERGENCE_TIMEOUT_MS = 60_000;

  private LoadConsistency() {}

  /**
   * A {@code waitForIt} check result: {@code true}, or the diagnostic shown when the wait times out.
   *
   * @param accepted whether the result is the expected one
   * @param diagnostic what to report otherwise
   * @return {@code Boolean.TRUE} or the diagnostic
   */
  static Object accept(boolean accepted, String diagnostic) {
    return accepted ? Boolean.TRUE : diagnostic;
  }

  /**
   * What the event store holds for the mutations of one or more loads.
   *
   * @param counterEvents the {@code _id}s of the stored {@code CounterAdded} events of each counter
   * @param cartEvents the {@code _id}s of the stored {@code CartItemChanged} events of each cart of the loads
   * @param cartItems the {@code itemsCount} of each cart mutation
   */
  record StoredState(Map<String, List<String>> counterEvents, Map<String, List<String>> cartEvents,
      Map<String, Integer> cartItems) {

    /**
     * @param counterId a counter
     * @return how many events it has
     */
    int countOf(String counterId) {
      return counterEvents.getOrDefault(counterId, List.of()).size();
    }

    /**
     * @return the total number of stored {@code CounterAdded} events
     */
    int incrementsStored() {
      return counterEvents.values().stream().mapToInt(List::size).sum();
    }
  }

  /**
   * The read models that miss events because of the at-most-once window (rule 4).
   *
   * @param counters counter id to its {@code amount}
   * @param carts carts whose read model does not exist yet although their events are stored
   */
  record Lagging(Map<String, Integer> counters, List<String> carts) {

    boolean isEmpty() {
      return counters.isEmpty() && carts.isEmpty();
    }
  }

  /**
   * Checks rules 1 to 3 on the data files.
   *
   * @param store the application's data files
   * @param counterIds every counter the loads incremented
   * @param mutations every mutation the loads sent
   * @return the stored state, for the convergence checks
   */
  @SuppressWarnings("unchecked")
  static StoredState assertStoredOnceAndAllOrNothing(StoreFiles store, List<String> counterIds,
      List<Mutation> mutations) {
    Map<String, List<String>> counterEvents = new TreeMap<>();
    Map<String, List<String>> storedIdentifiers = new LinkedHashMap<>();
    for (String counterId : counterIds) {
      List<Map<String, Object>> events = store.events("CounterAdded", counterId);
      counterEvents.put(counterId, events.stream().map(event -> String.valueOf(event.get("_id"))).toList());
      for (Map<String, Object> event : events) {
        String identifier = String.valueOf(((Map<String, Object>) event.get("value")).get("identifier"));
        storedIdentifiers.computeIfAbsent(identifier, key -> new ArrayList<>()).add(counterId);
      }
    }
    Map<String, Mutation> increments = mutations.stream().filter(mutation -> mutation.kind() == Kind.INCREMENT_COUNTER)
        .collect(Collectors.toMap(Mutation::identifier, Function.identity()));

    List<String> problems = new ArrayList<>();
    for (Mutation increment : increments.values()) {
      List<String> stored = storedIdentifiers.getOrDefault(increment.identifier(), List.of());
      if (increment.acknowledged() && !stored.equals(List.of(increment.entityID()))) {
        problems.add("acknowledged " + increment.identifier() + " on " + increment.entityID() + " is stored as "
            + stored);
      } else if (!increment.acknowledged() && stored.size() > 1) {
        problems.add("unacknowledged " + increment.identifier() + " is stored " + stored.size() + " times");
      } else if (!stored.isEmpty() && !stored.getFirst().equals(increment.entityID())) {
        problems.add(increment.identifier() + " is stored on " + stored + " instead of " + increment.entityID());
      }
    }
    storedIdentifiers.keySet().stream()
        .filter(identifier -> !increments.containsKey(identifier) && !identifier.startsWith(EXTRA_PREFIX))
        .forEach(identifier -> problems.add("stored event " + identifier + " comes from no mutation of the load"));

    Map<String, List<String>> cartEvents = new TreeMap<>();
    Map<String, Integer> cartItems = new TreeMap<>();
    for (Mutation cart : mutations) {
      if (cart.kind() != Kind.CHANGE_MULTIPLE_CART_ITEMS) {
        continue;
      }
      List<Map<String, Object>> events = store.events("CartItemChanged", cart.entityID());
      int stored = events.size();
      cartEvents.put(cart.entityID(), events.stream().map(event -> String.valueOf(event.get("_id"))).toList());
      cartItems.put(cart.entityID(), cart.itemsCount());
      if (cart.acknowledged() && stored != cart.itemsCount()) {
        problems.add("acknowledged cart mutation " + cart.entityID() + " has " + stored + " of " + cart.itemsCount()
            + " events stored");
      } else if (!cart.acknowledged() && stored != 0 && stored != cart.itemsCount()) {
        problems.add("unacknowledged cart mutation " + cart.entityID() + " is half applied: " + stored + " of "
            + cart.itemsCount() + " events stored");
      }
    }
    assertThat(problems).as("consistency of the event store after the crash").isEmpty();
    return new StoredState(counterEvents, cartEvents, cartItems);
  }

  /** Prefix of the identifiers of increments sent outside the loads (they are not reported as invented). */
  static final String EXTRA_PREFIX = "extra-";

  /**
   * Checks rules 4 and 5: waits until every read model reflects the stored events, failing at once on a value that
   * reveals a double projection.
   *
   * @param client a GraphQL client of the restarted application
   * @param store its data files
   * @param state what {@link #assertStoredOnceAndAllOrNothing} found
   * @param interruptedEventIds the events the recovery reported as interrupted (at-most-once window)
   * @return the read models that miss interrupted events (empty when nothing was interrupted)
   */
  @SuppressWarnings("unchecked")
  static Lagging assertReadModelsConverge(GraphQLClient client, StoreFiles store, StoredState state,
      Set<String> interruptedEventIds) {
    Map<String, Integer> laggingCounters = new TreeMap<>();
    List<String> laggingCarts = new ArrayList<>();
    for (Map.Entry<String, List<String>> counter : state.counterEvents().entrySet()) {
      String counterId = counter.getKey();
      int expected = counter.getValue().size();
      int interrupted = (int) counter.getValue().stream().filter(interruptedEventIds::contains).count();
      if (expected == 0) {
        assertThat(client.query(Documents.COUNTER_READ_MODEL, variables("id", counterId)).get("CounterReadModel"))
            .as("counter without events %s", counterId).isNull();
        continue;
      }
      GraphQLResponse converged = Sleep.waitForIt(
          () -> client.query(Documents.COUNTER_READ_MODEL, variables("id", counterId)),
          response -> {
            int amount = response.get("CounterReadModel.amount") instanceof Number number ? number.intValue() : 0;
            assertThat(amount).as("amount of counter %s (no double projection)", counterId)
                .isLessThanOrEqualTo(expected);
            return accept(amount >= expected - interrupted, "counter " + counterId + " has amount " + amount
                + ", the store has " + expected + " CounterAdded events (" + interrupted + " interrupted)");
          }, 250, CONVERGENCE_TIMEOUT_MS);
      int amount = converged.get("CounterReadModel.amount") instanceof Number number ? number.intValue() : 0;
      if (amount < expected) {
        laggingCounters.put(counterId, amount);
      }
      if (amount == 0) {
        continue; // every event of the counter was interrupted before its first projection
      }
      assertThat(converged.get("CounterReadModel.id")).isEqualTo(counterId);
      Map<String, Object> metadata = (Map<String, Object>) store.readModel("CounterReadModel", counterId)
          .get("boosterMetadata");
      assertThat(((Number) metadata.get("version")).intValue())
          .as("projections stored for counter %s (no double projection)", counterId)
          .isBetween(1, expected);
    }
    for (Map.Entry<String, List<String>> cart : state.cartEvents().entrySet()) {
      String cartId = cart.getKey();
      int items = state.cartItems().get(cartId);
      if (cart.getValue().isEmpty()) {
        assertThat(client.query(Documents.CART_READ_MODEL, variables("cartId", cartId)).get("CartReadModel"))
            .as("cart without events %s", cartId).isNull();
        continue;
      }
      boolean interrupted = cart.getValue().stream().anyMatch(interruptedEventIds::contains);
      GraphQLResponse converged = Sleep.waitForIt(
          () -> client.query(Documents.CART_READ_MODEL, variables("cartId", cartId)),
          response -> {
            Object size = response.get("CartReadModel.cartItemsSize");
            return accept((interrupted && response.get("CartReadModel") == null)
                    || Integer.valueOf(items).equals(size instanceof Number number ? number.intValue() : null),
                "cart " + cartId + " has " + size + " items, the store has " + items + " events");
          }, 250, CONVERGENCE_TIMEOUT_MS);
      if (converged.get("CartReadModel") == null) {
        laggingCarts.add(cartId);
        continue;
      }
      assertCartItems(converged, items);
    }
    return new Lagging(laggingCounters, laggingCarts);
  }

  /**
   * @param cart a {@link Documents#CART_READ_MODEL} answer
   * @param items the expected number of {@code ChangeMultipleCartItems} items ({@code "0"}, {@code "1"}...)
   */
  @SuppressWarnings("unchecked")
  static void assertCartItems(GraphQLResponse cart, int items) {
    List<Map<String, Object>> cartItems = (List<Map<String, Object>>) cart.get("CartReadModel.cartItems");
    assertThat(cartItems).extracting(item -> item.get("productId"))
        .containsExactlyInAnyOrderElementsOf(IntStream.range(0, items).mapToObj(Integer::toString).toList());
    assertThat(cartItems).allSatisfy(item -> assertThat(((Number) item.get("quantity")).intValue()).isEqualTo(1));
  }
}
