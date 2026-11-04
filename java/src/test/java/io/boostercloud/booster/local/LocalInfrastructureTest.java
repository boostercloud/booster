package io.boostercloud.booster.local;

import io.boostercloud.booster.core.*;
import io.boostercloud.booster.example.ShoppingCartApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class LocalInfrastructureTest {
  @TempDir Path root;
  private LocalConfiguration config(Path data) {
    return new LocalConfiguration(ShoppingCartApplication.class.getName(), data.toString(), "127.0.0.1", 0);
  }
  public static final class UnhealthyApplication implements LocalApplication {
    public UnhealthyApplication() {}
    public Booster create(LocalProvider provider) { return new ShoppingCartApplication().create(provider); }
    public java.util.Map<String,java.util.function.BooleanSupplier> rocketHealthChecks(Booster runtime, LocalProvider provider) {
      return java.util.Map.of("unavailable", () -> false);
    }
  }
  public static final class FailingSetupApplication implements LocalApplication {
    static final java.util.concurrent.atomic.AtomicBoolean workerClosedBeforeRuntime = new java.util.concurrent.atomic.AtomicBoolean();
    public FailingSetupApplication() {}
    public Booster create(LocalProvider provider) { return new ShoppingCartApplication().create(provider); }
    public void configure(Booster runtime, io.boostercloud.booster.transport.BoosterHttpServer server, LocalProvider provider) {
      provider.own(() -> { runtime.position(); workerClosedBeforeRuntime.set(true); });
      throw new IllegalStateException("setup failed");
    }
  }
  @Test void failedStartupTerminatesOwnedChildAndReleasesProvider() throws Exception {
    Path data = root.resolve("failed-child");
    var configuration = new LocalConfiguration(UnhealthyApplication.class.getName(), data.toString(), "127.0.0.1", 0);
    Path file = LocalInfrastructure.synth(configuration, root.resolve("unhealthy.json"));
    var error = assertThrows(IOException.class, () -> LocalInfrastructure.startDetached(file));
    assertTrue(error.getMessage().contains("503"), error.getMessage());
    try (var reopened = new LocalProvider(data)) { assertEquals(0, reopened.eventStore().lastPosition()); }
  }
  @Test void setupFailureClosesWorkersBeforeTheirRuntime() throws Exception {
    FailingSetupApplication.workerClosedBeforeRuntime.set(false);
    var configuration = new LocalConfiguration(FailingSetupApplication.class.getName(), root.resolve("failed-setup").toString(), "127.0.0.1", 0);
    assertThrows(IllegalStateException.class, () -> LocalHost.run(configuration, System.out));
    assertTrue(FailingSetupApplication.workerClosedBeforeRuntime.get());
    try (var reopened = new LocalProvider(configuration.directory())) { assertEquals(0, reopened.eventStore().lastPosition()); }
  }
  @Test void eventsAndProjectionVersionsSurviveProviderRestart() throws Exception {
    Path data = root.resolve("data");
    try (var provider = new LocalProvider(data); var app = new ShoppingCartApplication().create(provider)) {
      app.executeCommand("OpenCart", Json.MAPPER.valueToTree(new ShoppingCartApplication.OpenCart("one")), new RequestContext("create-one", UserContext.anonymous(), java.util.Map.of()));
      assertFalse(provider.stateStore().scan("readModels:CartView", null, 100).isEmpty());
      assertThrows(IOException.class, () -> new LocalProvider(data));
    }
    try (var provider = new LocalProvider(data); var app = new ShoppingCartApplication().create(provider)) {
      assertEquals("one", app.getReadModel("CartView", "one", RequestContext.anonymous()).path("id").asText());
      assertEquals(1, app.position());
    }
  }
  @Test void rejectsForeignDirectoriesAndSymlinkedDatabaseFiles() throws Exception {
    Path foreign = Files.createDirectory(root.resolve("foreign"));
    Files.writeString(foreign.resolve("keep.txt"), "user work");
    assertThrows(IOException.class, () -> new LocalProvider(foreign));
    assertEquals("user work", Files.readString(foreign.resolve("keep.txt")));
    Path data = root.resolve("data");
    try (var provider = new LocalProvider(data)) { }
    Files.delete(data.resolve("events.mv.db"));
    Files.createSymbolicLink(data.resolve("events.mv.db"), foreign.resolve("keep.txt"));
    assertThrows(IOException.class, () -> new LocalProvider(data));
    assertEquals("user work", Files.readString(foreign.resolve("keep.txt")));
  }
  @Test void cleanupRequiresExactDirectoryOwnershipAndExclusiveLock() throws Exception {
    Path data = root.resolve("data");
    try (var provider = new LocalProvider(data)) {
      assertThrows(IllegalArgumentException.class, () -> LocalInfrastructure.nuke(config(data), root));
      assertThrows(IOException.class, () -> LocalInfrastructure.nuke(config(data), data));
      assertTrue(Files.isRegularFile(data.resolve("events.mv.db")));
    }
    LocalInfrastructure.nuke(config(data), data);
    assertFalse(Files.exists(data));
    assertTrue(Files.exists(LocalProvider.lifecycleLockPath(data)));
    try (var fresh = new LocalProvider(data)) { assertEquals(0, fresh.eventStore().lastPosition()); }
  }
  @Test void configuredPathsResolveFromConfigurationFileAndSynthPreservesExistingFiles() throws Exception {
    Path file = root.resolve("local.json");
    LocalInfrastructure.synth(config(Path.of("nested data")), file);
    assertEquals(root.resolve("nested data"), LocalConfiguration.read(file).directory());
    assertThrows(FileAlreadyExistsException.class, () -> LocalInfrastructure.synth(config(root.resolve("other")), file));
  }
  @Test void realChildProcessSupportsHealthCommandsStopRebuildRestartAndCleanup() throws Exception {
    Path data = root.resolve("application data");
    Path file = LocalInfrastructure.synth(config(data), root.resolve("application.json"));
    LocalConfiguration configuration = LocalConfiguration.read(file);
    long first = LocalInfrastructure.startDetached(file);
    try {
      var status = LocalInfrastructure.status(configuration);
      assertEquals(first, status.path("pid").asLong());
      int port = status.path("port").asInt();
      var client = HttpClient.newHttpClient();
      var deniedStop = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/__booster/shutdown"))
          .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(401, deniedStop.statusCode());
      var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/graphql"))
          .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json").header("Idempotency-Key", "persistent-cart")
          .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"mutation { OpenCart(input:{cartId:\\\"persisted\\\"}) {id} }\"}")).build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode()); assertFalse(Json.MAPPER.readTree(response.body()).has("errors"), response.body());
      assertThrows(IOException.class, () -> LocalInfrastructure.startDetached(file));
      assertThrows(IOException.class, () -> LocalInfrastructure.rebuild(configuration));
      assertThrows(IOException.class, () -> LocalInfrastructure.nuke(configuration, data));
      LocalInfrastructure.stop(configuration);
      assertEquals(1, LocalInfrastructure.rebuild(configuration));
      long second = LocalInfrastructure.startDetached(file); assertNotEquals(first, second);
      var restarted = LocalInfrastructure.status(configuration);
      var query = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + restarted.path("port").asInt() + "/graphql"))
          .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ CartView(id:\\\"persisted\\\") { id } }\"}")).build(), HttpResponse.BodyHandlers.ofString());
      assertEquals("persisted", Json.MAPPER.readTree(query.body()).at("/data/CartView/id").asText(), query.body());
      LocalInfrastructure.stop(configuration);
      LocalInfrastructure.nuke(configuration, data); assertFalse(Files.exists(data));
    } finally {
      if (Files.exists(data.resolve(LocalHost.RUNTIME_FILE))) {
        try { LocalInfrastructure.stop(configuration); } catch (Exception ignored) { }
      }
      ProcessHandle.of(first).filter(ProcessHandle::isAlive).ifPresent(ProcessHandle::destroy);
    }
  }
}
