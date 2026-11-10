package com.boostercloud.framework.provider.local.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.boostercloud.framework.provider.local.library.ApiAdapter;
import com.boostercloud.framework.types.ScheduleInterface;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link LocalServer#close()}: stop accepting, finish in-flight work, release the stores. */
class GracefulShutdownTest {

  @TempDir
  Path directory;

  private ServerFixture fixture;
  private FakeUserApp app;

  @BeforeEach
  void setUp() {
    fixture = new ServerFixture();
    app = new FakeUserApp(fixture.config);
  }

  @AfterEach
  void tearDown() {
    fixture.close();
  }

  private static boolean accepts(int port) {
    try (Socket socket = new Socket("localhost", port)) {
      return socket.isConnected();
    } catch (IOException e) {
      return false;
    }
  }

  @Test
  void inFlightRequestsFinishWhileNewConnectionsAreRefused() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    app.onGraphQL(request -> {
      entered.countDown();
      try {
        release.await(20, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return ApiAdapter.requestSucceeded(Map.of("data", Map.of("slow", true)), Map.of());
    });
    LocalServer server = fixture.start(directory, app);
    HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    CompletableFuture<HttpResponse<String>> inFlight = client.sendAsync(
        HttpRequest.newBuilder(URI.create(server.graphqlURL())).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

    CompletableFuture<Void> closing = CompletableFuture.runAsync(server::close);
    await().until(() -> !accepts(server.port()));
    assertThat(closing).isNotDone();

    release.countDown();
    HttpResponse<String> response = inFlight.get(10, TimeUnit.SECONDS);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{\"data\":{\"slow\":true}}");
    closing.get(10, TimeUnit.SECONDS);
    assertThat(server.isRunning()).isFalse();
    assertThat(server.awaitStopped(Duration.ZERO)).isTrue();
  }

  @Test
  void closingStopsTheSchedulerTheWebsocketServerAndTheStores() {
    fixture.config.scheduledCommandHandlers.put("CheckCarts", new ScheduledCommandMetadata(Object.class,
        new ScheduleInterface("*", null, null, null, null, null)));
    LocalServer server = fixture.start(directory, app);
    assertThat(server.runtime().webSocketServerAdapter().isRunning()).isTrue();

    server.close();
    server.close();

    assertThat(server.scheduler().isStopped()).isTrue();
    assertThat(server.runtime().webSocketServerAdapter().isRunning()).isFalse();
    assertThat(accepts(server.port())).isFalse();
    fixture.clock.advance(Duration.ofMinutes(5));
    assertThat(app.scheduledTriggers).isEmpty();
  }

  @Test
  void theRuntimeCanBeKeptOpen() {
    var runtime = ServerFixture.runtime(directory, app);
    LocalServer server = Infrastructure.infrastructure(null, fixture.options(runtime).withCloseRuntimeOnStop(false))
        .startServer(fixture.config, 0);

    server.close();

    assertThat(runtime.webSocketServerAdapter().isRunning()).isTrue();
    runtime.close();
  }
}
