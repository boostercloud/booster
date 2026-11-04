package io.boostercloud.booster.local;

import io.boostercloud.booster.core.Booster;
import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.transport.BoosterHttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Runs a complete local application and exposes authenticated loopback lifecycle control. */
public final class LocalHost {
  static final String RUNTIME_FILE = ".runtime.json";
  private LocalHost() {}
  public static void run(LocalConfiguration config, PrintStream output) throws Exception {
    var application = config.instantiate();
    var shutdownComplete = new CountDownLatch(1);
    var authentication = application.authentication();
    var tasks = List.copyOf(application.schedules());
    try (var provider = new LocalProvider(config.directory()); var runtime = application.create(provider);
         AutoCloseable workerShutdown = provider::stopWorkers;
         var server = new BoosterHttpServer(runtime, new InetSocketAddress(config.host(), config.port()))) {
      server.authentication(authentication);
      var health = provider.own(io.boostercloud.booster.health.ProviderHealth.defaults(runtime, () -> true,
          () -> "http://127.0.0.1:" + server.port() + "/graphql", List.of(provider.directory().toString()), application.rocketHealthChecks(runtime, provider))
          .configureAll(true, false, true)
          .authorize(io.boostercloud.booster.health.HealthAuthorizer.policy(io.boostercloud.booster.core.AccessPolicy.publicAccess())).build());
      server.health(new io.boostercloud.booster.health.HealthHttpHandler(health, authentication));
      application.configure(runtime, server, provider);
      if (!tasks.isEmpty()) {
        provider.own(new io.boostercloud.booster.scheduling.PersistentScheduler(runtime, tasks))
            .start(java.time.Duration.ofSeconds(1), 100);
      }
      var stopped = new CountDownLatch(1);
      byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
      String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
      String instance = UUID.randomUUID().toString();
      server.route("/__booster/status", exchange -> {
        try (exchange) {
          if (!exchange.getRequestURI().getPath().equals("/__booster/status")) { exchange.sendResponseHeaders(404, -1); return; }
          if (!exchange.getRequestMethod().equals("GET")) { exchange.sendResponseHeaders(405, -1); return; }
          if (!controlAuthorized(exchange, secret)) { exchange.sendResponseHeaders(401, -1); return; }
          var report = health.check(new io.boostercloud.booster.health.HealthRequest("",
              io.boostercloud.booster.core.UserContext.anonymous(), Map.of("transport", "local-control")));
          byte[] body = Json.MAPPER.writeValueAsBytes(Map.of("status", report.healthy() ? "UP" : "DOWN",
              "position", runtime.position(), "components", report.components()));
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.getResponseHeaders().set("Cache-Control", "no-store");
          exchange.sendResponseHeaders(report.healthy() ? 200 : 503, body.length);
          exchange.getResponseBody().write(body);
        }
      });
      server.route("/__booster/shutdown", exchange -> {
        try (exchange) {
          if (!exchange.getRequestURI().getPath().equals("/__booster/shutdown")) { exchange.sendResponseHeaders(404, -1); return; }
          if (!exchange.getRequestMethod().equals("POST")) { exchange.sendResponseHeaders(405, -1); return; }
          if (!controlAuthorized(exchange, secret)) {
            exchange.sendResponseHeaders(401, -1); return;
          }
          exchange.sendResponseHeaders(202, -1);
        }
        stopped.countDown();
      });
      Thread hook = new Thread(() -> {
        stopped.countDown();
        try { shutdownComplete.await(20, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
      }, "booster-local-shutdown");
      Runtime.getRuntime().addShutdownHook(hook);
      Path metadata = config.directory().resolve(RUNTIME_FILE);
      try {
        server.start();
        writePrivate(metadata, Json.MAPPER.writeValueAsString(Map.of("pid", ProcessHandle.current().pid(),
            "instance", instance, "applicationClass", config.applicationClass(), "port", server.port(), "token", secret)));
        output.println("Booster local application started: http://127.0.0.1:" + server.port());
        output.println("Data: " + config.directory()); output.flush();
        stopped.await();
      } finally {
        if (Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) {
          var state = Json.MAPPER.readTree(Files.readString(metadata));
          if (instance.equals(state.path("instance").asText())) Files.deleteIfExists(metadata);
        }
        try { Runtime.getRuntime().removeShutdownHook(hook); } catch (IllegalStateException shuttingDown) { }
      }
    } finally { shutdownComplete.countDown(); }
  }
  private static boolean controlAuthorized(com.sun.net.httpserver.HttpExchange exchange, String secret) {
    if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) return false;
    var values = exchange.getRequestHeaders().get("Authorization");
    String authorization = values == null || values.size() != 1 ? "" : values.getFirst();
    return MessageDigest.isEqual(("Bearer " + secret).getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8));
  }
  static void writePrivate(Path file, String text) throws IOException {
    Path temporary;
    if (Files.getFileStore(file.getParent()).supportsFileAttributeView("posix"))
      temporary = Files.createTempFile(file.getParent(), ".runtime-", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    else temporary = Files.createTempFile(file.getParent(), ".runtime-", ".tmp");
    try { Files.writeString(temporary, text); Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
    finally { Files.deleteIfExists(temporary); }
  }
}
