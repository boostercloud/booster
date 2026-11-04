package io.boostercloud.booster.azure.hosting;

import com.azure.messaging.webpubsub.models.GetClientAccessTokenOptions;
import io.boostercloud.booster.azure.*;
import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.scheduling.PersistentScheduler;
import io.boostercloud.booster.transport.BoosterHttpServer;
import io.boostercloud.booster.transport.auth.AuthenticationException;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Azure Container Apps entry point using the same Java 27 runtime and domain APIs as local hosting. */
public final class AzureHost {
  private AzureHost() {}
  public static void main(String[] args) throws Exception {
    String name = System.getenv("BOOSTER_APPLICATION_CLASS");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("BOOSTER_APPLICATION_CLASS is required");
    var application = Class.forName(name).asSubclass(AzureApplication.class).getConstructor().newInstance();
    var config = AzureProvider.Config.fromEnvironment();
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid PORT");
    var shutdownComplete = new CountDownLatch(1);
    var tasks = List.copyOf(application.schedules());
    try (var provider = AzureProvider.open(config); var runtime = application.create(provider);
         AutoCloseable workerShutdown = provider::stopWorkers;
         var server = new BoosterHttpServer(runtime, new InetSocketAddress("0.0.0.0", port));
         var health = io.boostercloud.booster.health.ProviderHealth.defaults(runtime, () -> true,
             () -> "/graphql", List.of(config.cosmosEndpoint()), application.rocketHealthChecks(runtime, provider))
             .configureAll(true, false, true)
             .authorize(io.boostercloud.booster.health.HealthAuthorizer.policy(io.boostercloud.booster.core.AccessPolicy.publicAccess())).build();
         var scheduler = new PersistentScheduler(runtime, tasks)) {
      var authentication = application.authentication();
      server.authentication(authentication);
      server.health(new io.boostercloud.booster.health.HealthHttpHandler(health, authentication));
      application.configure(runtime, server);
      var uploads = application.fileUploads();
      if (Boolean.parseBoolean(System.getenv("BOOSTER_AZURE_UPLOADS_ENABLED")) && uploads.isEmpty())
        throw new IllegalStateException("Infrastructure enables uploads but AzureApplication.fileUploads() is empty");
      uploads.ifPresent(rocket -> server.route("/azure/files", new io.boostercloud.booster.rockets.uploads.AzureFileUploadHttpHandler(
          runtime, rocket, UpstreamAuthentication.bearer(System.getenv("BOOSTER_AZURE_UPSTREAM_TOKEN")),
          io.boostercloud.booster.core.AccessPolicy.authenticated())));
      var events = new AzureWebPubSubHandler(runtime, server.graphql(), authentication, provider.subscriptions(), provider.webPubSub());
      server.route("/azure/webpubsub", new UpstreamAuthentication(System.getenv("BOOSTER_AZURE_UPSTREAM_TOKEN"),
          new AzureWebPubSubHttpHandler(events, URI.create(config.webPubSubEndpoint()))));
      server.route("/azure/negotiate", exchange -> {
        try (exchange) {
          if (!exchange.getRequestURI().getPath().equals("/azure/negotiate")) { exchange.sendResponseHeaders(404, -1); return; }
          if (!exchange.getRequestMethod().equals("POST")) { exchange.sendResponseHeaders(405, -1); return; }
          var values = exchange.getRequestHeaders().get("Authorization");
          if (values != null && values.size() != 1) { exchange.sendResponseHeaders(401, -1); return; }
          try {
            var user = authentication.authenticate(exchange.getRequestHeaders().getFirst("Authorization"));
            var options = new GetClientAccessTokenOptions().setExpiresAfter(Duration.ofMinutes(10));
            if (user.authenticated()) options.setUserId(user.subject());
            var token = provider.webPubSub().getClientAccessToken(options);
            byte[] body = Json.MAPPER.writeValueAsBytes(Map.of("url", token.getUrl(), "protocol", "graphql-ws"));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
          } catch (AuthenticationException rejected) { exchange.sendResponseHeaders(401, -1); }
        }
      });
      String worker = config.application() + ":" + UUID.randomUUID();
      provider.startEventPipeline(runtime, worker);
      provider.startSubscriptionNotifications(events, worker);
      if (!tasks.isEmpty()) scheduler.start(Duration.ofSeconds(1), 100);
      var stopped = new CountDownLatch(1);
      var shutdown = new Thread(() -> {
        stopped.countDown();
        try { shutdownComplete.await(55, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
      }, "booster-azure-shutdown");
      Runtime.getRuntime().addShutdownHook(shutdown);
      try {
        server.start(); System.out.println("Booster Azure host listening on port " + port); stopped.await();
      } finally { try { Runtime.getRuntime().removeShutdownHook(shutdown); } catch (IllegalStateException ignored) { } }
    } finally { shutdownComplete.countDown(); }
  }
}
