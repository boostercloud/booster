package com.boostercloud.rocket.webhook.types;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code WebhookRequest} ({@code webhook-request.ts}, {@code { req: any; [key: string]: unknown }}): the
 * request the rocket function receives through {@code boosterRocketDispatcher}. The transports build it as a raw map,
 * exactly like TypeScript:
 *
 * <ul>
 *   <li>local: {@code { BOOSTER_ROCKET_FUNCTION_ID: "rocket-webhook", req: <WebhookHttpRequest> }} (the id travels in
 *       the request because every rocket shares the local process);</li>
 *   <li>Azure ({@code BoosterAzureFunctions.rocketHttp}): {@code { req: { method, url, originalUrl, headers, query,
 *       params, rawBody, body } }} (the id is the function app setting).</li>
 * </ul>
 *
 * <p>{@link #from(Object)} turns either form into this record; custom authorizers receive it.
 *
 * @param req the HTTP request, or {@code null} when the raw request had none
 * @param properties the other members of the raw request ({@code BOOSTER_ROCKET_FUNCTION_ID}...)
 */
public record WebhookRequest(WebhookHttpRequest req, Map<String, Object> properties) {

  /** Canonical constructor: copies the properties. */
  public WebhookRequest {
    properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
  }

  /**
   * @param req the HTTP request
   * @return a request without other properties
   */
  public static WebhookRequest of(WebhookHttpRequest req) {
    return new WebhookRequest(req, null);
  }

  /**
   * Reads the raw rocket request: a {@code WebhookRequest}, or a map whose {@code req} is a
   * {@link WebhookHttpRequest} (local) or a raw map (Azure). Anything else gives a request without {@code req}
   * (TypeScript fails later, reading {@code request.req.url}).
   *
   * @param raw the raw request
   * @return the request
   * @throws IllegalArgumentException {@code Cannot read properties of undefined (reading 'req')} for {@code null}
   */
  public static WebhookRequest from(Object raw) {
    if (raw == null) {
      throw new IllegalArgumentException("Cannot read properties of undefined (reading 'req')");
    }
    if (raw instanceof WebhookRequest request) {
      return request;
    }
    if (!(raw instanceof Map<?, ?> map)) {
      return new WebhookRequest(null, null);
    }
    Map<String, Object> properties = new LinkedHashMap<>();
    map.forEach((key, value) -> {
      if (!"req".equals(key)) {
        properties.put(String.valueOf(key), value);
      }
    });
    Object req = map.get("req");
    WebhookHttpRequest httpRequest = switch (req) {
      case WebhookHttpRequest typed -> typed;
      case Map<?, ?> rawReq -> WebhookHttpRequest.fromRaw(rawReq);
      case null, default -> null;
    };
    return new WebhookRequest(httpRequest, properties);
  }
}
