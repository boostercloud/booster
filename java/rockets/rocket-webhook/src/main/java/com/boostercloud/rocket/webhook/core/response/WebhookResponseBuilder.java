package com.boostercloud.rocket.webhook.core.response;

import com.boostercloud.framework.types.ConsoleLogger;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.rocket.webhook.core.Js;
import com.boostercloud.rocket.webhook.core.response.responses.WebhookEmptyResponse;
import com.boostercloud.rocket.webhook.core.response.responses.WebhookFileResponse;
import com.boostercloud.rocket.webhook.core.response.responses.WebhookJsonResponse;
import com.boostercloud.rocket.webhook.core.response.responses.WebhookTextResponse;
import com.boostercloud.rocket.webhook.types.WebhookHandlerFileReturnType;
import com.boostercloud.rocket.webhook.types.WebhookHandlerJsonReturnType;
import com.boostercloud.rocket.webhook.types.WebhookHandlerReturnType;
import com.boostercloud.rocket.webhook.types.WebhookHandlerTextReturnType;
import com.boostercloud.rocket.webhook.types.WebhookResponseType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code webhook-response-builder.ts}: picks the strategy for a handler result and merges its headers.
 *
 * <ul>
 *   <li>a falsy result ({@code null}, {@code false}, {@code 0}, {@code ""}): {@link WebhookEmptyResponse};</li>
 *   <li>a {@link WebhookHandlerReturnType}: the strategy of its {@code responseType};</li>
 *   <li>a raw map ({@code { body, headers?, responseType, file? }}, the TypeScript object literal): the strategy of its
 *       {@code responseType} value ({@code text/plain}, {@code application/json}, {@code file});</li>
 *   <li>anything else (an unknown or missing {@code responseType}): logs {@code Unexpected response type. Using File}
 *       and uses {@link WebhookFileResponse}, which fails when there is no {@code file}.</li>
 * </ul>
 *
 * <p>Headers, later ones winning (insertion order kept, keys compared case-sensitively as the TypeScript object
 * spread does): {@link #DEFAULT_HEADERS}, the result's {@code headers}, the type-specific ones.
 */
public final class WebhookResponseBuilder {

  /** {@code DEFAULT_HEADERS}: {@code Access-Control-Allow-Origin: *}. */
  public static final Map<String, Object> DEFAULT_HEADERS =
      Collections.unmodifiableMap(new LinkedHashMap<>(Map.of("Access-Control-Allow-Origin", "*")));

  private final WebhookHandlerReturnType response;
  private final WebhookResponseClass webhookBaseResponse;

  /**
   * @param response the handler result
   */
  public WebhookResponseBuilder(Object response) {
    this(response, ConsoleLogger.INSTANCE);
  }

  /**
   * @param response the handler result
   * @param logger where {@code Unexpected response type. Using File} goes (TypeScript: {@code console.log})
   */
  public WebhookResponseBuilder(Object response, Logger logger) {
    if (!Js.isTruthy(response)) {
      this.response = null;
      this.webhookBaseResponse = new WebhookEmptyResponse();
      return;
    }
    WebhookHandlerReturnType typed = toReturnType(response);
    WebhookResponseType responseType = responseType(response);
    this.response = typed;
    if (responseType == null) {
      logger.info("Unexpected response type. Using File");
      this.webhookBaseResponse = new WebhookFileResponse();
      return;
    }
    this.webhookBaseResponse = switch (responseType) {
      case FILE -> new WebhookFileResponse();
      case JSON -> new WebhookJsonResponse();
      case TEXT -> new WebhookTextResponse();
    };
  }

  /**
   * @return the result as a typed value ({@code null} for the empty response)
   */
  public WebhookHandlerReturnType response() {
    return response;
  }

  /**
   * @return the strategy
   */
  public WebhookResponseClass strategy() {
    return webhookBaseResponse;
  }

  /**
   * {@code getBody()}.
   *
   * @return the body
   */
  public Object getBody() {
    return webhookBaseResponse.getBody(response);
  }

  /**
   * {@code getHeaders()}: {@code { ...DEFAULT_HEADERS, ...response.headers, ...typeSpecificHeaders }}.
   *
   * @return the headers, in insertion order
   */
  public Map<String, Object> getHeaders() {
    Map<String, Object> headers = new LinkedHashMap<>(DEFAULT_HEADERS);
    if (response != null && response.headers() != null) {
      headers.putAll(response.headers());
    }
    Map<String, Object> customHeaders = webhookBaseResponse.getHeaders(response);
    headers.putAll(customHeaders);
    return headers;
  }

  private static WebhookResponseType responseType(Object response) {
    return switch (response) {
      case WebhookHandlerReturnType typed -> typed.responseType();
      case Map<?, ?> raw -> WebhookResponseType.fromValue(raw.get("responseType"));
      default -> null;
    };
  }

  private static WebhookHandlerReturnType toReturnType(Object response) {
    return switch (response) {
      case WebhookHandlerReturnType typed -> typed;
      case Map<?, ?> raw -> fromRaw(raw);
      // An object without the members of a handler result: nothing to send but a missing file
      default -> new WebhookHandlerFileReturnType(null, null, null);
    };
  }

  private static WebhookHandlerReturnType fromRaw(Map<?, ?> raw) {
    Object body = raw.get("body");
    Map<String, Object> headers = null;
    if (raw.get("headers") instanceof Map<?, ?> rawHeaders) {
      headers = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : rawHeaders.entrySet()) {
        headers.put(String.valueOf(entry.getKey()), entry.getValue());
      }
    }
    WebhookResponseType type = WebhookResponseType.fromValue(raw.get("responseType"));
    if (type == WebhookResponseType.TEXT) {
      return new WebhookHandlerTextReturnType(body, headers);
    }
    if (type == WebhookResponseType.JSON) {
      return new WebhookHandlerJsonReturnType(body, headers);
    }
    // file, or an unknown type handled as a file
    Object rawFile = raw.get("file");
    WebhookHandlerFileReturnType.File file = switch (rawFile) {
      case null -> null;
      case WebhookHandlerFileReturnType.File typedFile -> typedFile;
      case Map<?, ?> fileMap -> new WebhookHandlerFileReturnType.File(asString(fileMap.get("name")),
          asString(fileMap.get("mimeType")));
      // A primitive has no name nor mimeType
      default -> new WebhookHandlerFileReturnType.File(null, null);
    };
    return new WebhookHandlerFileReturnType(body, headers, file);
  }

  private static String asString(Object value) {
    return value == null ? null : Js.string(value);
  }
}
