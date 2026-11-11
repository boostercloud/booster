package com.boostercloud.integration.providerspecific.local.durability;

import static com.boostercloud.integration.providerspecific.local.durability.Documents.variables;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.integration.harness.AppProcess;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import com.boostercloud.integration.harness.Faker;
import com.boostercloud.integration.harness.FreePorts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Durability case 3 (Java-only, beyond TypeScript): data files damaged while the application was down.
 *
 * <p>What it proves: (a) a torn last line (what a write cut by a crash leaves: a partial JSON document without its
 * newline) appended to {@code events.json} and {@code read_models.json} after a {@code SIGKILL} does not stop the
 * application: it starts, discards the torn line with a warning naming the file, compacts the file (the line is gone
 * from disk) and serves exactly the data it served before the crash, and later appends are not glued to the torn
 * bytes; (b) a few corrupt lines (at most 10 % of a file, NeDB's {@code corruptAlertThreshold}) are skipped with a
 * warning; (c) with more than 10 % of corrupt lines the application refuses to start: the process exits with code 1
 * after reporting NeDB's {@code ... Cautiously refusing to start NeDB to prevent dataloss.}, without touching the file
 * or keeping its port; (d) once the file is repaired it starts again on the same data.
 */
@EndToEnd(EndToEnd.Application.OWN)
@DisplayName("Durability: torn writes and corrupt data files")
class TornWriteIT {

  private static final String TORN_EVENT =
      "{\"version\":1,\"kind\":\"event\",\"superKind\":\"domain\",\"entityID\":\"torn-";
  private static final String TORN_READ_MODEL = "{\"typeName\":\"CounterReadModel\",\"value\":{\"id\":\"torn";

  private final AppProcess app;
  private final ApplicationUnderTest applicationUnderTest;
  private final List<String> counterIds = IntStream.range(0, 3).mapToObj(i -> Faker.random.uuid()).toList();
  private final String cartId = Faker.random.uuid();
  private GraphQLClient client;
  private StoreFiles store;
  private Map<String, Object> answersBeforeTheCrash;

  TornWriteIT(AppProcess app, ApplicationUnderTest applicationUnderTest) {
    this.app = app;
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void writeSomeData() {
    client = applicationUnderTest.graphql().client();
    store = StoreFiles.of(app);
    for (int round = 0; round < 4; round++) {
      for (String counterId : counterIds) {
        assertThat(client.mutate(Documents.INCREMENT_COUNTER,
            variables("counterId", counterId, "identifier", "round-" + round)).get("IncrementCounter")).isEqualTo(true);
      }
    }
    assertThat(client.mutate(Documents.CHANGE_MULTIPLE_CART_ITEMS, variables("cartId", cartId, "itemsCount", 4))
        .get("ChangeMultipleCartItems")).isEqualTo(true);
    answersBeforeTheCrash = answers();
  }

  /** What the application serves for the data written by this suite (the read models and the event search). */
  private Map<String, Object> answers() {
    Map<String, Object> answers = new LinkedHashMap<>();
    for (String counterId : counterIds) {
      answers.put("counter " + counterId, client.query(Documents.COUNTER_READ_MODEL, variables("id", counterId))
          .toMap());
      answers.put("events " + counterId, client.query(Documents.COUNTER_EVENTS, variables("counterId", counterId))
          .toMap());
    }
    answers.put("cart", client.query(Documents.CART_READ_MODEL, variables("cartId", cartId)).toMap());
    answers.put("cart events", client.query(Documents.CART_EVENTS, variables("cartId", cartId)).toMap());
    return answers;
  }

  private static void assertEveryLineIsADocument(List<String> lines) {
    assertThat(lines).allSatisfy(line -> assertThat(BoosterJson.parseObject(line)).isNotNull());
  }

  @Test
  @Order(1)
  @DisplayName("serves the data written before the crash")
  void servesTheDataWrittenBeforeTheCrash() {
    for (String counterId : counterIds) {
      GraphQLResponse counter = client.query(Documents.COUNTER_READ_MODEL, variables("id", counterId));
      assertThat(counter.get("CounterReadModel.identifier")).isEqualTo("round-0");
      assertThat(((Number) counter.get("CounterReadModel.amount")).intValue()).isEqualTo(4);
    }
    assertThat(store.events("CounterAdded")).hasSize(12);
  }

  @Test
  @Order(2)
  @DisplayName("starts after a crash left a torn last line in its data files, discarding and reporting it")
  void startsAfterATornLastLine() {
    assertThat(app.kill()).isEqualTo(AppProcess.SIGKILL_EXIT_CODE);
    store.append(StoreFiles.EVENTS, TORN_EVENT);
    store.append(StoreFiles.READ_MODELS, TORN_READ_MODEL);
    assertThat(store.lines(StoreFiles.EVENTS).getLast()).isEqualTo(TORN_EVENT);

    app.start();

    String output = RecoveryLog.outputOfStart(app.output(), app.name(), 2);
    assertThat(output)
        .contains("Discarding an incomplete last line of ")
        .contains("events.json (interrupted write): " + TORN_EVENT)
        .contains("read_models.json (interrupted write): " + TORN_READ_MODEL);
    assertThat(store.lines(StoreFiles.EVENTS)).doesNotContain(TORN_EVENT);
    assertThat(store.lines(StoreFiles.READ_MODELS)).doesNotContain(TORN_READ_MODEL);
    assertEveryLineIsADocument(store.lines(StoreFiles.EVENTS));
    assertEveryLineIsADocument(store.lines(StoreFiles.READ_MODELS));
    assertThat(answers()).isEqualTo(answersBeforeTheCrash);
  }

  @Test
  @Order(3)
  @DisplayName("keeps every later write on a line of its own")
  void keepsEveryLaterWriteOnALineOfItsOwn() {
    String counterId = counterIds.getFirst();
    assertThat(client.mutate(Documents.INCREMENT_COUNTER, variables("counterId", counterId, "identifier", "after"))
        .get("IncrementCounter")).isEqualTo(true);
    assertThat(store.events("CounterAdded", counterId)).hasSize(5);
    app.restart();
    assertEveryLineIsADocument(store.lines(StoreFiles.EVENTS));
    assertThat(((Number) client.query(Documents.COUNTER_READ_MODEL, variables("id", counterId))
        .get("CounterReadModel.amount")).intValue()).isEqualTo(5);
    answersBeforeTheCrash = answers();
  }

  @Test
  @Order(4)
  @DisplayName("skips a few corrupt lines, at most 10 % of a file, and reports them")
  void skipsAFewCorruptLines() {
    assertThat(app.kill()).isEqualTo(AppProcess.SIGKILL_EXIT_CODE);
    int lines = store.lines(StoreFiles.EVENTS).size();
    assertThat(lines).isGreaterThanOrEqualTo(20);
    int corrupt = lines / 20;
    store.append(StoreFiles.EVENTS, "this is not a document\n".repeat(corrupt));

    app.start();

    assertThat(RecoveryLog.outputOfStart(app.output(), app.name(), app.starts()))
        .contains("Skipped " + corrupt + " corrupt line(s) of ");
    // compacted: the corrupt lines are gone (the scheduled command may have appended a run since)
    assertThat(store.lines(StoreFiles.EVENTS)).hasSizeGreaterThanOrEqualTo(lines)
        .doesNotContain("this is not a document");
    assertThat(answers()).isEqualTo(answersBeforeTheCrash);
  }

  @Test
  @Order(5)
  @DisplayName("refuses to start when more than 10 % of a data file is corrupt, leaving the file untouched")
  void refusesToStartWhenMoreThanTenPercentIsCorrupt() {
    assertThat(app.kill()).isEqualTo(AppProcess.SIGKILL_EXIT_CODE);
    byte[] healthy = store.bytes(StoreFiles.EVENTS);
    int lines = store.lines(StoreFiles.EVENTS).size();
    int corrupt = lines / 4 + 1;
    store.append(StoreFiles.EVENTS, "this is not a document\n".repeat(corrupt));
    byte[] damaged = store.bytes(StoreFiles.EVENTS);
    int percent = (int) Math.floor(100.0 * corrupt / (lines + corrupt));

    assertThatThrownBy(app::start)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("The application " + app.name() + " exited with code 1 before it was ready");

    assertThat(app.isAlive()).isFalse();
    assertThat(app.exitCode()).isEqualTo(1);
    String nedbMessage = percent + "% of the data file is corrupt, more than given corruptAlertThreshold (10%). "
        + "Cautiously refusing to start NeDB to prevent dataloss.";
    assertThat(RecoveryLog.outputOfStart(app.output(), app.name(), app.starts()))
        .contains("Could not load the data files of " + app.boosterDirectory() + "; the server does not start: "
            + nedbMessage)
        .contains("The 'start' operation failed")
        .doesNotContain("Local server listening on");
    assertThat(store.bytes(StoreFiles.EVENTS)).as("events.json after the refused start").isEqualTo(damaged);
    assertThat(FreePorts.isFree(app.httpPort())).isTrue();

    store.write(StoreFiles.EVENTS, healthy);
  }

  @Test
  @Order(6)
  @DisplayName("starts again on the same data once the file is repaired")
  void startsAgainOnceTheFileIsRepaired() {
    app.start();
    assertThat(answers()).isEqualTo(answersBeforeTheCrash);
    List<String> problems = new ArrayList<>();
    for (String counterId : counterIds) {
      int events = store.events("CounterAdded", counterId).size();
      Object amount = client.query(Documents.COUNTER_READ_MODEL, variables("id", counterId))
          .get("CounterReadModel.amount");
      if (!(amount instanceof Number number) || number.intValue() != events) {
        problems.add(counterId + ": " + events + " events, amount " + amount);
      }
    }
    assertThat(problems).isEmpty();
  }
}
