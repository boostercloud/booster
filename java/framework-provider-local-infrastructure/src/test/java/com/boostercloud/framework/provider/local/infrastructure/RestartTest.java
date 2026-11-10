package com.boostercloud.framework.provider.local.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.local.LocalProviderRuntime;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalTestHelper;
import com.boostercloud.framework.provider.local.library.ApiAdapter;
import com.boostercloud.framework.provider.local.store.CorruptDataFileException;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stopping and starting the server over the same {@code .booster} directory, start-up crash recovery and the start-up
 * load of damaged data files.
 */
class RestartTest {

  @TempDir
  Path directory;

  private ServerFixture fixture;

  @BeforeEach
  void setUp() {
    fixture = new ServerFixture();
  }

  @AfterEach
  void tearDown() {
    fixture.close();
  }

  private static NonPersistedEventEnvelope event(String cartId, int quantity) {
    return NonPersistedEventEnvelope.builder()
        .version(1).superKind(SuperKindType.DOMAIN).entityID(UUID.of(cartId)).requestID(UUID.generate())
        .entityTypeName("Cart").typeName("CartItemChanged").value(Map.of("cartId", cartId, "quantity", quantity))
        .build();
  }

  /** An application whose mutation stores one event of the cart named in the request. */
  private FakeUserApp storingApp() {
    FakeUserApp app = new FakeUserApp(fixture.config);
    app.onGraphQL(request -> {
      var body = (Map<?, ?>) ((com.boostercloud.framework.provider.local.library.LocalHttpRequest) request).body();
      app.provider().events().store(List.of(event((String) body.get("cartId"), 1)), fixture.config);
      return ApiAdapter.requestSucceeded(Map.of("data", Map.of("stored", true)), Map.of());
    });
    return app;
  }

  private LocalTestHelper helper(LocalServer server) {
    return LocalTestHelper.build("my-store", LocalTestHelper.Options.defaults().withBaseDirectory(directory)
        .withPorts(server.port(), server.runtime().webSocketServerAdapter().port()).withReadinessTimeout(null));
  }

  @Test
  void dataSurvivesARestart() {
    LocalServer first = fixture.start(directory, storingApp());
    assertThat(Http1Client.postJson(first.graphqlURL(), "{\"cartId\":\"cart-1\"}").statusCode()).isEqualTo(200);
    assertThat(Http1Client.postJson(first.graphqlURL(), "{\"cartId\":\"cart-2\"}").statusCode()).isEqualTo(200);
    assertThat(helper(first).counters().events()).isEqualTo(2);
    first.close();

    FakeUserApp secondApp = storingApp();
    LocalServer second = fixture.start(directory, secondApp);
    LocalTestHelper helper = helper(second);
    assertThat(helper.counters().events()).isEqualTo(2);
    assertThat(second.recoveredEvents()).isEmpty();
    assertThat(secondApp.processedEvents).isEmpty();

    assertThat(Http1Client.postJson(second.graphqlURL(), "{\"cartId\":\"cart-1\"}").statusCode()).isEqualTo(200);

    assertThat(helper.counters().events()).isEqualTo(3);
    List<Map<String, Object>> cartEvents = helper.queries().events("Cart-cart-1-event");
    assertThat(cartEvents).hasSize(2);
    assertThat((String) cartEvents.get(0).get("createdAt")).isGreaterThanOrEqualTo((String) cartEvents.get(1).get("createdAt"));
    assertThat(second.runtime().eventRegistry().count(Map.of())).isEqualTo(3);
  }

  @Test
  void eventsLeftUndispatchedByACrashAreDispatchedAtStartUp() throws IOException {
    Path crashImage = directory.resolve("crash");
    FakeUserApp crashing = storingApp();
    AtomicBoolean copied = new AtomicBoolean();
    crashing.onDispatch(events -> {
      if (copied.compareAndSet(false, true)) {
        copyTree(directory.resolve("live/.booster"), crashImage.resolve(".booster"));
      }
    });
    LocalServer live = fixture.start(directory.resolve("live"), crashing);
    assertThat(Http1Client.postJson(live.graphqlURL(), "{\"cartId\":\"cart-9\"}").statusCode()).isEqualTo(200);
    assertThat(crashing.processedEvents).hasSize(1);
    live.close();

    FakeUserApp recovered = new FakeUserApp(fixture.config);
    LocalServer restarted = fixture.start(crashImage, recovered);

    assertThat(restarted.recoveredEvents()).extracting(EventEnvelope::entityID).containsExactly(UUID.of("cart-9"));
    assertThat(recovered.processedEvents).extracting(EventEnvelope::entityID).containsExactly(UUID.of("cart-9"));
    restarted.close();

    FakeUserApp again = new FakeUserApp(fixture.config);
    LocalServer third = fixture.start(crashImage, again);
    assertThat(third.recoveredEvents()).isEmpty();
    assertThat(again.processedEvents).isEmpty();
  }

  private static final String VALID_EVENT_LINE = "{\"kind\":\"event\",\"entityTypeName\":\"Cart\",\"entityID\":\"cart-1\","
      + "\"typeName\":\"CartItemChanged\",\"value\":{\"cartId\":\"cart-1\"},\"createdAt\":\"2026-11-10T09:15:27.412Z\","
      + "\"_id\":\"Xk2pQ9aLm0TzB7cd\"}";

  @Test
  void aDataFileWithTooManyCorruptLinesFailsTheStartAndIsLeftUntouched() throws IOException {
    Path events = Files.createDirectories(directory.resolve(".booster")).resolve("events.json");
    String contents = VALID_EVENT_LINE + "\n{\"kind\":\"event\",\"entityTy\n" + "garbage\n";
    Files.writeString(events, contents);
    LocalProviderRuntime runtime = ServerFixture.runtime(directory, new FakeUserApp(fixture.config));
    try {
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> Infrastructure.infrastructure(List.of(),
              fixture.options(runtime)).startServer(fixture.config, 0))
          .isInstanceOf(CorruptDataFileException.class)
          .hasMessage("66% of the data file is corrupt, more than given corruptAlertThreshold (10%). Cautiously "
              + "refusing to start NeDB to prevent dataloss.");
      assertThat(Files.readString(events)).isEqualTo(contents);
      org.mockito.Mockito.verify(fixture.logger).error("[Booster]|LocalInfrastructure#start: ",
          "Could not load the data files of " + directory.resolve(".booster").toAbsolutePath()
              + "; the server does not start: 66% of the data file is corrupt, more than given corruptAlertThreshold "
              + "(10%). Cautiously refusing to start NeDB to prevent dataloss.");
    } finally {
      runtime.close();
    }
  }

  @Test
  void aTornLastLineAndFewCorruptLinesAreDiscardedBeforeTheServerServes() throws IOException {
    Path events = Files.createDirectories(directory.resolve(".booster")).resolve("events.json");
    StringBuilder contents = new StringBuilder();
    for (int i = 0; i < 10; i++) {
      contents.append(VALID_EVENT_LINE.replace("Xk2pQ9aLm0TzB7cd", "Xk2pQ9aLm0TzB7c" + i)).append('\n');
    }
    contents.append("not json\n");                                     // 1 corrupt line of 11: under 10 %
    contents.append("{\"kind\":\"event\",\"entityTypeName\":\"Ca");     // torn last line: not counted
    Files.writeString(events, contents.toString());

    LocalServer server = fixture.start(directory, new FakeUserApp(fixture.config));

    // loaded (and compacted) at start-up, before any request touched the store
    assertThat(server.runtime().eventRegistry().isLoaded()).isTrue();
    List<String> lines = Files.readAllLines(events);
    assertThat(lines).hasSize(10).allSatisfy(line -> assertThat(line).startsWith("{\"kind\":\"event\""));
    assertThat(server.runtime().eventRegistry().count(Map.of())).isEqualTo(10);
  }

  @Test
  void missingDataFilesAreStillCreatedOnFirstUse() {
    LocalServer server = fixture.start(directory, storingApp());
    assertThat(Files.exists(directory.resolve(".booster/events.json"))).isFalse();
    assertThat(Files.exists(directory.resolve(".booster/read_models.json"))).isFalse();
    assertThat(Http1Client.postJson(server.graphqlURL(), "{\"cartId\":\"cart-1\"}").statusCode()).isEqualTo(200);
    assertThat(Files.exists(directory.resolve(".booster/events.json"))).isTrue();
  }

  private static void copyTree(Path from, Path to) {
    try (Stream<Path> files = Files.list(from)) {
      Files.createDirectories(to);
      for (Path file : files.toList()) {
        Files.copy(file, to.resolve(file.getFileName().toString()));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
