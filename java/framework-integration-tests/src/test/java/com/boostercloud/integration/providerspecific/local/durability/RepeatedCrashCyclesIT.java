package com.boostercloud.integration.providerspecific.local.durability;

import static com.boostercloud.integration.providerspecific.local.durability.Documents.variables;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.Sleep;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.integration.harness.AppProcess;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import com.boostercloud.integration.harness.Faker;
import com.boostercloud.integration.providerspecific.local.durability.CommandLoad.Mutation;
import com.boostercloud.integration.providerspecific.local.durability.LoadConsistency.Lagging;
import com.boostercloud.integration.providerspecific.local.durability.LoadConsistency.StoredState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Durability case 4 (Java-only, beyond TypeScript): six cycles of concurrent writes, {@code SIGKILL} in the middle of
 * them and restart, on the same data files, with the consistency rules of {@link LoadConsistency} checked after each
 * restart over everything written since the first cycle, and a final graceful restart.
 *
 * <p>What it proves: crashes do not accumulate damage. Each restart loads (and compacts) files that the previous
 * crash may have cut, recovers the undispatched events of that crash only, and keeps every mutation acknowledged in
 * any earlier cycle stored exactly once; read models keep converging (a counter interrupted in one cycle catches up
 * with the next cycle's increments), and the application keeps accepting work after each recovery.
 */
@EndToEnd(EndToEnd.Application.OWN)
@DisplayName("Durability: repeated write, kill and restart cycles")
class RepeatedCrashCyclesIT {

  private static final int CART_ITEMS = 6;
  private static final int ACKNOWLEDGED_PER_CYCLE = 60;

  private final AppProcess app;
  private final ApplicationUnderTest applicationUnderTest;
  private final List<String> counterIds = IntStream.range(0, 3).mapToObj(i -> Faker.random.uuid()).toList();
  private final List<Mutation> mutations = new ArrayList<>();
  private final Set<String> interruptedEventIds = new LinkedHashSet<>();
  private GraphQLClient client;
  private StoreFiles store;
  private StoredState state;
  private Lagging lagging;
  private long eventsBefore;

  RepeatedCrashCyclesIT(AppProcess app, ApplicationUnderTest applicationUnderTest) {
    this.app = app;
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    client = applicationUnderTest.graphql().client();
    store = StoreFiles.of(app);
  }

  @ParameterizedTest(name = "cycle {0}: write, kill, restart and check everything written so far")
  @Order(1)
  @ValueSource(ints = {1, 2, 3, 4, 5, 6})
  void writeKillRestartAndCheck(int cycle) {
    eventsBefore = applicationUnderTest.count().events();
    List<Mutation> sent;
    try (CommandLoad load = CommandLoad.start(applicationUnderTest, "cycle" + cycle, counterIds, 8, CART_ITEMS,
        0.3)) {
      load.awaitAcknowledged(ACKNOWLEDGED_PER_CYCLE, Duration.ofMinutes(2));
      assertThat(app.kill()).isEqualTo(AppProcess.SIGKILL_EXIT_CODE);
      sent = load.stop();
    }
    mutations.addAll(sent);
    app.start();
    assertThat(app.starts()).isEqualTo(cycle + 1);
    RecoveryLog recovery = RecoveryLog.of(app, cycle + 1);
    interruptedEventIds.addAll(recovery.interruptedEventIds());
    System.out.println("[durability] cycle " + cycle + ": " + sent.size() + " mutations sent, "
        + sent.stream().filter(Mutation::acknowledged).count() + " acknowledged; recovery: " + recovery);

    state = LoadConsistency.assertStoredOnceAndAllOrNothing(store, counterIds, mutations);
    assertThat(applicationUnderTest.count().events()).isGreaterThan(eventsBefore);
    lagging = LoadConsistency.assertReadModelsConverge(client, store, state, interruptedEventIds);
    assertThat(lagging.carts()).allSatisfy(cartId -> assertThat(state.cartEvents().get(cartId))
        .anyMatch(interruptedEventIds::contains));
  }

  @Test
  @Order(2)
  @DisplayName("keeps everything after a final graceful restart and closes the at-most-once gaps")
  void keepsEverythingAfterAFinalGracefulRestart() {
    assertThat(app.restart().starts()).isEqualTo(8);
    assertThat(RecoveryLog.of(app, 8).interruptedEventIds()).isEmpty();
    assertThat(RecoveryLog.of(app, 8).redispatched()).isZero();
    StoredState afterRestart = LoadConsistency.assertStoredOnceAndAllOrNothing(store, counterIds, mutations);
    assertThat(afterRestart.counterEvents()).isEqualTo(state.counterEvents());
    assertThat(afterRestart.cartEvents()).isEqualTo(state.cartEvents());

    for (String counterId : counterIds) {
      assertThat(client.mutate(Documents.INCREMENT_COUNTER, variables("counterId", counterId,
          "identifier", LoadConsistency.EXTRA_PREFIX + "final-" + counterId)).get("IncrementCounter")).isEqualTo(true);
      int expected = afterRestart.countOf(counterId) + 1;
      Sleep.waitForIt(() -> client.query(Documents.COUNTER_READ_MODEL, variables("id", counterId)),
          response -> response.get("CounterReadModel.amount") instanceof Number amount
              && amount.intValue() == expected, 200, LoadConsistency.CONVERGENCE_TIMEOUT_MS);
    }
    assertThat(Map.of("entries", store.documents(StoreFiles.DISPATCH_JOURNAL)))
        .isEqualTo(Map.of("entries", List.of()));
  }
}
