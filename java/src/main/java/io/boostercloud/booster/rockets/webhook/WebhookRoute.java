package io.boostercloud.booster.rockets.webhook;

import io.boostercloud.booster.core.AccessPolicy;
import java.util.*;

/** Route takes precedence over the deprecated origin, matching the original rocket. */
public record WebhookRoute(String route, String origin, Set<String> allowedMethods,
    AccessPolicy authorize, WebhookAuthorizer requestAuthorizer, MultipartConfig multipart,
    WebhookHandler handler) {
  public WebhookRoute {
    String effective = route != null ? route : origin;
    if (effective == null || effective.isBlank()) throw new IllegalArgumentException("Undefined route parameter");
    validateRoute(effective);
    allowedMethods = allowedMethods == null ? Set.of("POST") : Set.copyOf(allowedMethods);
    if (allowedMethods.isEmpty() || !Set.of("GET", "POST").containsAll(allowedMethods))
      throw new IllegalArgumentException("Webhook methods must be GET and/or POST");
    // The TypeScript rocket skips authorization when the configuration omits it.
    authorize = authorize == null ? AccessPolicy.publicAccess() : authorize;
    multipart = multipart == null ? MultipartConfig.defaults() : multipart;
    Objects.requireNonNull(handler, "handler");
  }
  public WebhookRoute(String route, WebhookHandler handler) { this(route, null, null, null, null, null, handler); }
  public String effectiveRoute() { return route != null ? route : origin; }
  public String path() {
    String name = effectiveRoute();
    if (name.startsWith("/")) name = name.substring(1);
    return name.startsWith("webhook/") ? "/" + name : "/webhook/" + name;
  }
  private static void validateRoute(String route) {
    if (route.startsWith("/")) route = route.substring(1);
    if (route.isBlank() || route.endsWith("/") || route.contains("//")) throw new IllegalArgumentException("Invalid webhook route");
    for (String part : route.split("/")) {
      if (!part.matches("[A-Za-z0-9._~-]+") || part.equals(".") || part.equals(".."))
        throw new IllegalArgumentException("Invalid webhook route");
    }
  }
}
