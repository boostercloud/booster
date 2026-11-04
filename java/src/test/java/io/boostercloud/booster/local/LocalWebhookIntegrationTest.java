package io.boostercloud.booster.local;

import io.boostercloud.booster.core.*;
import io.boostercloud.booster.rockets.webhook.*;
import io.boostercloud.booster.transport.BoosterHttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LocalWebhookIntegrationTest {
  @TempDir Path temporary;
  public record Received(String id) {}
  public record Receipt(String id, int count) {}
  public record ReceiptView(String id, int count) {}
  public static final class Application implements LocalApplication {
    static final AtomicInteger calls = new AtomicInteger();
    public Application() {}
    public Booster create(LocalProvider provider) {
      return Booster.builder(provider.eventStore()).stateStore(provider.stateStore()).entity(Receipt.class)
          .event(Received.class, Received::id, Receipt.class, (event, previous) -> new Receipt(event.id(), previous == null ? 1 : previous.count() + 1))
          .readModel(ReceiptView.class, Receipt.class, AccessPolicy.publicAccess(), (entity, previous) -> new ReceiptView(entity.id(), entity.count())).build();
    }
    public void configure(Booster runtime, BoosterHttpServer server, LocalProvider provider) {
      server.route("/webhook", WebhookRocket.builder(runtime).route("received", (request, register) -> {
        calls.incrementAndGet(); register.emit(new Received("one")); return WebhookResponse.text("accepted");
      }).build().handler());
    }
  }
  @Test void hostedWebhookReplaysAfterFullLocalLifecycleRestartAndProjectsThroughGraphQL() throws Exception {
    Application.calls.set(0);
    var configuration = new LocalConfiguration(Application.class.getName(), temporary.resolve("data").toString(), "127.0.0.1", 0);
    for (int round = 0; round < 2; round++) {
      var ready = new CountDownLatch(1);
      try (var workers = Executors.newSingleThreadExecutor(); var client = HttpClient.newHttpClient();
           var output = new PrintStream(OutputStream.nullOutputStream()) {
             @Override public void println(String text) { if (text.startsWith("Booster local application started:")) ready.countDown(); }
           }) {
        Future<?> running = workers.submit(() -> { LocalHost.run(configuration, output); return null; });
        try {
          assertTrue(ready.await(10, TimeUnit.SECONDS), "Local host did not become ready");
          int port = LocalInfrastructure.status(configuration).path("port").asInt();
          var delivery = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/webhook/received"))
              .header("Idempotency-Key", "same-delivery").header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
          var response = client.send(delivery, HttpResponse.BodyHandlers.ofString());
          assertEquals(200, response.statusCode()); assertEquals("accepted", response.body());
          var query = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/graphql"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ ReceiptView(id: \\\"one\\\") { count } }\"}")).build();
          var projection = client.send(query, HttpResponse.BodyHandlers.ofString());
          assertEquals(200, projection.statusCode());
          assertEquals(1, Json.MAPPER.readTree(projection.body()).path("data").path("ReceiptView").path("count").asInt(), projection.body());
          var denied = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/__booster/shutdown"))
              .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
          assertEquals(401, denied.statusCode());
          LocalInfrastructure.stop(configuration); running.get(10, TimeUnit.SECONDS);
        } finally { if (!running.isDone()) running.cancel(true); }
      }
    }
    assertEquals(1, Application.calls.get());
  }
}
