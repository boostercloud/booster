package com.boostercloud.framework.provider.local;

import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/** The Java-only start-up steps over the {@code .booster} directory: its lock and the stale websocket rows. */
class DataDirectoryTest {

  @TempDir
  Path project;

  private final List<LocalProviderRuntime> runtimes = new ArrayList<>();
  private BoosterConfig config;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = Mockito.mock(Logger.class);
  }

  @AfterEach
  void tearDown() {
    runtimes.forEach(LocalProviderRuntime::close);
  }

  private LocalProviderRuntime runtime(boolean webSocketServer) {
    FakeUserApp app = new FakeUserApp(config);
    LocalProviderRuntime runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(project).withWebSocketServer(webSocketServer, "0").withUserApp(() -> app));
    runtimes.add(runtime);
    app.provider(new LocalProvider(runtime, List.of(), Provider::loadInfrastructurePackage));
    return runtime;
  }

  @Test
  void onlyOneRuntimeAtATimeCanLockTheDataDirectory() {
    LocalProviderRuntime first = runtime(false);
    first.lockDataDirectory();
    first.lockDataDirectory();
    assertThat(project.resolve(".booster/booster.lock")).content().isEqualTo("pid " + ProcessHandle.current().pid()
        + "\n");

    LocalProviderRuntime second = runtime(false);
    assertThatThrownBy(second::lockDataDirectory)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Another Booster process (pid " + ProcessHandle.current().pid() + ") is using the data files of "
            + project.resolve(".booster").toAbsolutePath() + ". Stop it before starting this one: two processes on "
            + "the same .booster directory would lose each other's writes.");

    first.close();
    second.lockDataDirectory();
  }

  @Test
  void aDataDirectoryLockedByAnotherProcessIsRefusedUntilThatProcessEnds() throws Exception {
    String java = ProcessHandle.current().info().command().orElse(
        Path.of(System.getProperty("java.home"), "bin", "java").toString());
    Process child = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
        LockingMain.class.getName(), project.toString()).redirectErrorStream(true).start();
    try {
      BufferedReader output = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8));
      assertThat(output.readLine()).isEqualTo("LOCKED");
      LocalProviderRuntime runtime = runtime(false);
      assertThatThrownBy(runtime::lockDataDirectory)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageStartingWith("Another Booster process (pid " + child.pid() + ") is using the data files of");

      // the operating system releases the lock of a killed process
      child.destroyForcibly();
      assertThat(child.waitFor(30, TimeUnit.SECONDS)).isTrue();
      runtime.lockDataDirectory();
    } finally {
      child.destroyForcibly();
    }
  }

  @Test
  void connectionsAndSubscriptionsLeftByAProcessThatDiedAreRemovedAndLiveOnesKept() throws Exception {
    // what a process killed while a client had a subscription open leaves behind
    LocalProviderRuntime crashed = runtime(false);
    crashed.connectionRegistry().store(map("expirationTime", 1794525260, "connectionID", "dead-1"));
    crashed.subscriptionRegistry().store(map("connectionID", "dead-1", "className", "CartReadModel",
        "expirationTime", 1794525260));
    crashed.subscriptionRegistry().store(map("className", "CartReadModel"));
    crashed.close();

    LocalProviderRuntime restarted = runtime(true);
    HttpClient client = HttpClient.newHttpClient();
    WebSocket socket = client.newWebSocketBuilder()
        .buildAsync(URI.create("ws://localhost:" + restarted.webSocketServerAdapter().port() + "/websocket"),
            new WebSocket.Listener() { })
        .get(10, TimeUnit.SECONDS);
    try {
      Awaitility.await().atMost(Duration.ofSeconds(10))
          .until(() -> restarted.webSocketServerAdapter().connectionIds().size() == 1);
      String live = restarted.webSocketServerAdapter().connectionIds().iterator().next();
      restarted.connectionRegistry().store(map("expirationTime", 1794525260, "connectionID", live));
      restarted.subscriptionRegistry().store(map("connectionID", live, "className", "CartReadModel"));

      assertThat(restarted.removeStaleConnections()).isEqualTo(new LocalProviderRuntime.StaleConnections(1, 2));
      assertThat(restarted.connectionRegistry().query(map())).extracting(row -> row.get("connectionID"))
          .containsExactly(live);
      assertThat(restarted.subscriptionRegistry().query(map())).extracting(row -> row.get("connectionID"))
          .containsExactly(live);
      assertThat(restarted.removeStaleConnections()).isEqualTo(new LocalProviderRuntime.StaleConnections(0, 0));
    } finally {
      socket.abort();
    }
  }

  @Test
  void removingStaleConnectionsCreatesNoFile() {
    LocalProviderRuntime runtime = runtime(false);
    assertThat(runtime.removeStaleConnections()).isEqualTo(new LocalProviderRuntime.StaleConnections(0, 0));
    assertThat(Files.exists(project.resolve(".booster/connections.json"))).isFalse();
    assertThat(Files.exists(project.resolve(".booster/subscriptions.json"))).isFalse();
  }
}
