package com.boostercloud.rocket.webhook.local.controllers;

import com.boostercloud.framework.provider.local.infrastructure.Http;
import com.boostercloud.framework.provider.local.infrastructure.web.BodyParsers;
import com.boostercloud.framework.provider.local.infrastructure.web.HttpRequest;
import com.boostercloud.framework.provider.local.infrastructure.web.HttpResponse;
import com.boostercloud.framework.provider.local.infrastructure.web.NextFunction;
import com.boostercloud.framework.provider.local.infrastructure.web.Router;
import com.boostercloud.framework.types.UserAppRegistry;
import com.boostercloud.framework.types.rockets.Rockets;
import com.boostercloud.rocket.webhook.types.WebhookAPIResult;
import com.boostercloud.rocket.webhook.types.WebhookHttpRequest;
import com.boostercloud.rocket.webhook.types.WebhookParams;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Port of {@code controllers/webhook-controller.ts}: the routes of one webhook on the local server.
 *
 * <p>{@code POST /<route>} and {@code GET /<route>} are always registered, behind {@code express.raw()}
 * ({@link BodyParsers#raw()}: {@code application/octet-stream} bodies up to 100 kB as bytes), whatever the webhook's
 * {@code allowedMethods} (the dispatcher rejects the other methods). The request reaches the application as
 * {@code { BOOSTER_ROCKET_FUNCTION_ID: "rocket-webhook", req: <WebhookHttpRequest> }} through
 * {@code boosterRocketDispatcher}, and the result is written back:
 *
 * <ul>
 *   <li>success ({@code 200}): every result header, then the body as Express's {@code res.send} sends it (strings as
 *       UTF-8 with {@code ; charset=utf-8} added to the content type, bytes as they are, other values as JSON);</li>
 *   <li>failure: the status and {@code {"title", "reason"}} as JSON (the result headers are not sent, as in
 *       TypeScript);</li>
 *   <li>when {@code boosterRocketDispatcher} itself throws: {@code requestFailed(error)} and the error is passed on
 *       ({@code next(error)}).</li>
 * </ul>
 */
public final class WebhookController {

  private final String endpoint;
  private final Router router = new Router();
  private final Function<Object, Object> boosterRocketDispatcher;

  /**
   * @param endpoint the route of the webhook (without leading slash)
   */
  public WebhookController(String endpoint) {
    this(endpoint, request -> UserAppRegistry.get().boosterRocketDispatcher(request));
  }

  /**
   * @param endpoint the route of the webhook (without leading slash)
   * @param boosterRocketDispatcher the application's {@code boosterRocketDispatcher} (tests)
   */
  public WebhookController(String endpoint, Function<Object, Object> boosterRocketDispatcher) {
    this.endpoint = endpoint;
    this.boosterRocketDispatcher = Objects.requireNonNull(boosterRocketDispatcher, "boosterRocketDispatcher");
    router.post("/" + endpoint, BodyParsers.raw(), this::handleWebhook);
    router.get("/" + endpoint, BodyParsers.raw(), this::handleWebhook);
  }

  /**
   * @return the route
   */
  public String endpoint() {
    return endpoint;
  }

  /**
   * @return the router with the two routes
   */
  public Router router() {
    return router;
  }

  /**
   * {@code handleWebhook(req, res, next)}.
   *
   * @param req the request
   * @param res the response
   * @param next the next handler
   */
  public void handleWebhook(HttpRequest req, HttpResponse res, NextFunction next) {
    try {
      Map<String, Object> request = new LinkedHashMap<>();
      request.put(Rockets.ROCKET_FUNCTION_ID_ENV_VAR, WebhookParams.FUNCTION_ID);
      request.put("req", toWebhookHttpRequest(req));
      Object response = boosterRocketDispatcher.apply(request);
      int status = status(response);
      res.status(status);
      if (status == WebhookAPIResult.HTTP_SUCCESS_STATUS_CODE) {
        Object body = member(response, "body");
        setHeaders(response, res);
        res.send(body);
        return;
      }
      res.json(member(response, "body"));
    } catch (RuntimeException e) {
      Http.requestFailed(e, res);
      next.next(e);
    }
  }

  /**
   * The Express request as the rocket sees it ({@code req}): method, URLs, lower-cased headers, {@code qs}-parsed query,
   * route parameters, the raw bytes of JSON bodies, the parsed body and the unread body stream (multipart).
   *
   * @param req the local request
   * @return the rocket request
   */
  public static WebhookHttpRequest toWebhookHttpRequest(HttpRequest req) {
    return WebhookHttpRequest.builder(req.method(), req.url())
        .originalUrl(req.originalUrl())
        .headers(req.headers())
        .query(req.query())
        .params(req.params())
        .rawBody(req.rawBody())
        .body(req.body())
        .bodyStream(req::inputStream)
        .build();
  }

  private static void setHeaders(Object response, HttpResponse res) {
    if (member(response, "headers") instanceof Map<?, ?> headers) {
      for (Map.Entry<?, ?> header : headers.entrySet()) {
        res.setHeader(String.valueOf(header.getKey()), header.getValue());
      }
    }
  }

  private static int status(Object response) {
    if (member(response, "status") instanceof Number number) {
      return number.intValue();
    }
    throw new IllegalArgumentException("Invalid status code: undefined");
  }

  private static Object member(Object response, String name) {
    return switch (response) {
      case WebhookAPIResult result -> switch (name) {
        case "status" -> result.status();
        case "headers" -> result.headers();
        default -> result.body();
      };
      case Map<?, ?> raw -> raw.get(name);
      case null -> throw new IllegalArgumentException(
          "Cannot read properties of undefined (reading '" + name + "')");
      default -> null;
    };
  }
}
