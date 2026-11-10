package com.boostercloud.integration.providerspecific.local.durability;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.integration.harness.AppProcess;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Durability case 6 (Java-only, beyond TypeScript): the scheduled command {@code CheckCartCount}
 * ({@code @ScheduledCommand(minute = "0/1")}, a {@code CartChecked} event for {@code the-checked-cart} every minute)
 * across a graceful restart and across a crash.
 *
 * <p>What it proves: the scheduler is configured again by every start, so the command fires again after a
 * {@code SIGTERM} restart and after a {@code SIGKILL} and start; the events it registered before keep being stored and
 * counted ({@code CartReadModel.checks} equals the number of stored {@code CartChecked} events after each start, so
 * none was lost or projected twice).
 */
@EndToEnd(EndToEnd.Application.OWN)
@DisplayName("Durability: scheduled commands fire again after restarts")
class ScheduledCommandsAfterRestartIT {

  /** The command runs at second 0 of every minute: a run is at most a minute away (plus a margin). */
  private static final long NEXT_RUN_TIMEOUT_MS = 100_000;

  private final AppProcess app;
  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;
  private StoreFiles store;

  ScheduledCommandsAfterRestartIT(AppProcess app, ApplicationUnderTest applicationUnderTest) {
    this.app = app;
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    client = applicationUnderTest.graphql().client();
    store = StoreFiles.of(app);
  }

  private List<Map<String, Object>> checks() {
    return store.events("CartChecked", "the-checked-cart");
  }

  /** Waits for a run after {@code since}, then for the read model to count every stored run. */
  private List<Map<String, Object>> awaitARunAfter(Instant since, List<Map<String, Object>> earlierRuns) {
    List<Map<String, Object>> runs = waitForIt(this::checks, events -> LoadConsistency.accept(events.stream()
        .anyMatch(event -> Instant.parse(String.valueOf(event.get("createdAt"))).isAfter(since)),
        "no CartChecked event after " + since + " yet: " + events.size() + " in all"), 1000, NEXT_RUN_TIMEOUT_MS);
    assertThat(runs).containsAll(earlierRuns);
    GraphQLResponse cart = waitForIt(() -> client.query(Documents.CHECKED_CART),
        response -> response.get("CartReadModel.checks") instanceof Number checks
            && checks.intValue() == checks().size(), 500, 30_000);
    assertThat(cart.get("CartReadModel.id")).isEqualTo("the-checked-cart");
    return checks();
  }

  @Test
  @Order(1)
  @DisplayName("fires every minute before any restart")
  void firesBeforeAnyRestart() {
    List<Map<String, Object>> runs = awaitARunAfter(Instant.EPOCH, List.of());
    assertThat(runs).isNotEmpty().allSatisfy(run -> assertThat(run.get("typeName")).isEqualTo("CartChecked"));
  }

  @Test
  @Order(2)
  @DisplayName("fires again after a graceful restart, keeping the earlier runs")
  void firesAgainAfterAGracefulRestart() {
    List<Map<String, Object>> earlier = checks();
    app.restart();
    Instant restarted = Instant.now();
    List<Map<String, Object>> runs = awaitARunAfter(restarted, earlier);
    assertThat(runs).hasSizeGreaterThan(earlier.size());
  }

  @Test
  @Order(3)
  @DisplayName("fires again after a crash, keeping the earlier runs")
  void firesAgainAfterACrash() {
    List<Map<String, Object>> earlier = checks();
    assertThat(app.kill()).isEqualTo(AppProcess.SIGKILL_EXIT_CODE);
    app.start();
    Instant restarted = Instant.now();
    List<Map<String, Object>> runs = awaitARunAfter(restarted, earlier);
    assertThat(runs).hasSizeGreaterThan(earlier.size());
    assertThat(app.starts()).isEqualTo(3);
  }
}
