package io.boostercloud.booster.azure.hosting;

import com.sun.net.httpserver.*;
import io.boostercloud.booster.azure.AzureWebPubSubHandler;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded HTTP adapter. Mount behind UpstreamAuthentication before accepting CloudEvents. */
public final class AzureWebPubSubHttpHandler implements HttpHandler {
  private final AzureWebPubSubHandler events;
  private final URI service;
  public AzureWebPubSubHttpHandler(AzureWebPubSubHandler events, URI service) {
    this.events = Objects.requireNonNull(events); this.service = Objects.requireNonNull(service);
  }
  @Override public void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      if (!exchange.getRequestURI().getPath().equals("/azure/webpubsub")) { exchange.sendResponseHeaders(404, -1); return; }
      if (exchange.getRequestMethod().equals("OPTIONS")) {
        var origins = exchange.getRequestHeaders().get("WebHook-Request-Origin");
        String origin = origins != null && origins.size() == 1 ? origins.getFirst() : "";
        if (!origin.equals(service.getHost()) && !origin.equals(service.getScheme() + "://" + service.getHost())) {
          exchange.sendResponseHeaders(403, -1); return;
        }
        exchange.getResponseHeaders().set("WebHook-Allowed-Origin", origin);
        exchange.sendResponseHeaders(200, -1); return;
      }
      if (!exchange.getRequestMethod().equals("POST")) { exchange.sendResponseHeaders(405, -1); return; }
      var headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
      for (var header : exchange.getRequestHeaders().entrySet()) {
        if (header.getKey().toLowerCase(Locale.ROOT).startsWith("ce-") && header.getValue().size() != 1) {
          exchange.sendResponseHeaders(400, -1); return;
        }
        if (header.getValue().size() == 1) headers.put(header.getKey(), header.getValue().getFirst());
      }
      byte[] bytes = exchange.getRequestBody().readNBytes(1_048_577);
      if (bytes.length > 1_048_576) { exchange.sendResponseHeaders(413, -1); return; }
      try {
        var response = events.handleCloudEvent(headers, new String(bytes, StandardCharsets.UTF_8));
        response.headers().forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) exchange.getResponseBody().write(body);
      } catch (RuntimeException failure) { exchange.sendResponseHeaders(503, -1); }
    }
  }
}
