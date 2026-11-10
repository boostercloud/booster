package com.boostercloud.framework.provider.local.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalTestHelper;
import com.boostercloud.framework.provider.local.library.ApiAdapter;
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

/** Stopping and starting the server over the same {@code .booster} directory, and start-up crash recovery. */
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
