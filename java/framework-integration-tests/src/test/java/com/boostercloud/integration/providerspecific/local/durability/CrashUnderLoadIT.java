package com.boostercloud.integration.providerspecific.local.durability;

import static com.boostercloud.integration.providerspecific.local.durability.Documents.variables;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.Sleep;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.harness.AppProcess;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import com.boostercloud.integration.harness.Faker;
import com.boostercloud.integration.providerspecific.local.durability.CommandLoad.Mutation;
import com.boostercloud.integration.providerspecific.local.durability.LoadConsistency.Lagging;
import com.boostercloud.integration.providerspecific.local.durability.LoadConsistency.StoredState;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Durability case 2 (Java-only, beyond TypeScript): the application is killed with {@code SIGKILL} while 16 clients
 * send mutations concurrently, then started again on the same data files.
 *
 * <p>What it proves (the rules of {@link LoadConsistency}): every mutation acknowledged before the kill has all its
 * events stored after the restart, exactly once; a mutation in flight at the kill is applied entirely or not at all
 * (a {@code ChangeMultipleCartItems} of 8 events is never half stored); no event is invented or duplicated; the read
 * models converge to the state the stored events imply (the start-up recovery re-dispatches the events that were
 * stored but not dispatched) and no event is projected twice ({@code CounterReadModel.amount} counts events, so a
 * double reduction would show; {@code boosterMetadata.version} counts projections). An event whose processing had
 * started at the kill is not processed again (the framework's at-most-once rule, reported at start-up): its entity's
 * read model may miss it until the entity's next event, which the last test sends to show the gap closes.
 */
@EndToEnd(EndToEnd.Application.OWN)
@DisplayName("Durability: crash (SIGKILL) under concurrent command load")
class CrashUnderLoadIT {

  private static final int WORKERS = 16;
  private static final int CART_ITEMS = 8;
  private static final int ACKNOWLEDGED_BEFORE_KILL = 150;

  private final AppProcess app;
  private final ApplicationUnderTest applicationUnderTest;
  private final List<String> counterIds = IntStream.range(0, 4).mapToObj(i -> Faker.random.uuid()).toList();
  private GraphQLClient client;
  private StoreFiles store;
  private List<Mutation> mutations;
  private int killExitCode;
  private RecoveryLog recovery;
  private StoredState state;
  private Lagging lagging;

  CrashUnderLoadIT(AppProcess app, ApplicationUnderTest applicationUnderTest) {
    this.app = app;
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void crashUnderLoadAndRestart() {
    client = applicationUnderTest.graphql().client();
    store = StoreFiles.of(app);
    try (CommandLoad load = CommandLoad.start(applicationUnderTest, "crash", counterIds, WORKERS, CART_ITEMS, 0.25)) {
      load.awaitAcknowledged(ACKNOWLEDGED_BEFORE_KILL, Duration.ofMinutes(2));
      killExitCode = app.kill();
      mutations = load.stop();
    }
    app.start();
    recovery = RecoveryLog.of(app, 2);
    System.out.println("[durability] " + getClass().getSimpleName() + ": " + mutations.size() + " mutations sent, "
        + mutations.stream().filter(Mutation::acknowledged).count() + " acknowledged; recovery: " + recovery);
  }

  private StoredState storedState() {
    if (state == null) {
      state = LoadConsistency.assertStoredOnceAndAllOrNothing(store, counterIds, mutations);
    }
    return state;
  }

  @Test
  @Order(1)
  @DisplayName("is killed while mutations are in flight")
  void isKilledWhileMutationsAreInFlight() {
    assertThat(killExitCode).isEqualTo(AppProcess.SIGKILL_EXIT_CODE);
    assertThat(mutations).filteredOn(Mutation::acknowledged).hasSizeGreaterThanOrEqualTo(ACKNOWLEDGED_BEFORE_KILL);
    assertThat(mutations).filteredOn(mutation -> !mutation.acknowledged()).isNotEmpty();
    assertThat(app.starts()).isEqualTo(2);
    assertThat(recovery.output()).contains("Local server listening on " + "http://localhost:" + app.httpPort());
  }

  @Test
  @Order(2)
  @DisplayName("stores every acknowledged mutation exactly once and never half applies one")
  void storesEveryAcknowledgedMutationExactlyOnce() {
    long acknowledgedIncrements = mutations.stream()
        .filter(mutation -> mutation.acknowledged() && mutation.kind() == CommandLoad.Kind.INCREMENT_COUNTER).count();
    assertThat(storedState().incrementsStored()).isGreaterThanOrEqualTo((int) acknowledgedIncrements);
  }

  @Test
  @Order(3)
  @DisplayName("converges the read models to the stored events without double projections")
  void convergesTheReadModels() {
    lagging = LoadConsistency.assertReadModelsConverge(client, store, storedState(),
        Set.copyOf(recovery.interruptedEventIds()));
    if (!lagging.isEmpty()) {
      System.out.println("[durability] read models missing interrupted events (at-most-once): " + lagging);
    }
  }

  @Test
  @Order(4)
  @DisplayName("closes the at-most-once gap with the next event of each entity and keeps serving")
  void closesTheGapWithTheNextEventAndKeepsServing() {
    for (String counterId : counterIds) {
      assertThat(client.mutate(Documents.INCREMENT_COUNTER, variables("counterId", counterId,
          "identifier", LoadConsistency.EXTRA_PREFIX + "after-crash-" + counterId)).get("IncrementCounter"))
          .isEqualTo(true);
    }
    for (String counterId : counterIds) {
      int expected = storedState().countOf(counterId) + 1;
      assertThat(store.events("CounterAdded", counterId)).hasSize(expected);
      Sleep.waitForIt(() -> client.query(Documents.COUNTER_READ_MODEL, variables("id", counterId)),
          response -> response.get("CounterReadModel.amount") instanceof Number amount
              && amount.intValue() == expected, 200, LoadConsistency.CONVERGENCE_TIMEOUT_MS);
    }
    List<String> laggingCarts = lagging == null ? List.of() : lagging.carts();
    for (String cartId : laggingCarts) {
      assertThat(client.mutate(Documents.CHANGE_CART_ITEM,
          variables("cartId", cartId, "productId", "extra", "quantity", 1)).get("ChangeCartItem")).isEqualTo(true);
      GraphQLResponse cart = Sleep.waitForIt(
          () -> client.query(Documents.CART_READ_MODEL, variables("cartId", cartId)),
          response -> response.get("CartReadModel.cartItemsSize") instanceof Number size
              && size.intValue() == CART_ITEMS + 1, 200, LoadConsistency.CONVERGENCE_TIMEOUT_MS);
      assertThat(cart.get("CartReadModel.cartItemsIds")).asList()
          .contains("extra", "0", String.valueOf(CART_ITEMS - 1));
    }
    assertThat(Map.of("entries", store.documents(StoreFiles.DISPATCH_JOURNAL)))
        .as("dispatch journal once everything was dispatched").isEqualTo(Map.of("entries", List.of()));
  }
}
