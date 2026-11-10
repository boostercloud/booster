package com.boostercloud.framework.provider.azure.library;

import com.boostercloud.framework.provider.azure.helpers.JsValues;
import com.boostercloud.framework.types.TypeLevel;
import com.boostercloud.framework.types.errors.BoosterErrors;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Port of {@code library/api-adapter.ts}: the return values of the functions (HTTP responses and Web PubSub
 * responses).
 */
public final class ApiAdapter {

  /** {@code WEB_SOCKET_PROTOCOL_HEADER}. */
  public static final String WEB_SOCKET_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
  /** {@code WEB_SOCKET_MESSAGE_MARKER_HEADER} (added by core to websocket MESSAGE results). */
  public static final String WEB_SOCKET_MESSAGE_MARKER_HEADER = "X-Booster-WebSocket-Message";

  private ApiAdapter() {}

  /**
   * {@code requestSucceeded}, first match wins:
   *
   * <ol>
   *   <li>headers contain {@code Sec-WebSocket-Protocol} (exact case): {@link WebPubSubConnectResponse} with that
   *       subprotocol (first element of a list) — Web PubSub CONNECT;
   *   <li>{@code X-Booster-WebSocket-Message} is the string {@code true}: {@link WebPubSubMessageResponse}
   *       {@code { data: body, dataType: 'json' }} — Web PubSub MESSAGE;
   *   <li>falsy body and no headers: {@code null} — Web PubSub DISCONNECT;
   *   <li>otherwise an {@link AzureHttpResponse} with status 200, CORS and JSON headers plus the custom ones (without
   *       {@code X-Booster-*}; lists joined with {@code ,}), and {@code jsonBody} when the body is truthy.
   * </ol>
   *
   * @param body the result, or {@code null}
   * @param headers the response headers (values may be strings, numbers or lists), or {@code null}
   * @return the function's return value, or {@code null}
   */
  public static Object requestSucceeded(Object body, Map<String, ?> headers) {
    boolean isWebSocketConnect = headers != null && headers.containsKey(WEB_SOCKET_PROTOCOL_HEADER);
    if (isWebSocketConnect) {
      Object subprotocol = headers.get(WEB_SOCKET_PROTOCOL_HEADER);
      String value;
      if (subprotocol instanceof List<?> list) {
        value = list.isEmpty() || list.getFirst() == null ? null : list.getFirst().toString();
      } else {
        value = subprotocol == null ? "undefined" : JsValues.jsString(subprotocol);
      }
      return new WebPubSubConnectResponse(value, null, null, null);
    }

    boolean isWebSocketMessage = headers != null && "true".equals(headers.get(WEB_SOCKET_MESSAGE_MARKER_HEADER));
    if (isWebSocketMessage) {
      return new WebPubSubMessageResponse(body, "json");
    }

    // No body and no meaningful headers - nothing to return (e.g., DISCONNECT)
    if (!JsValues.truthy(body) && (headers == null || headers.isEmpty())) {
      return null;
    }

    Map<String, String> responseHeaders = defaultHeaders();
    if (headers != null) {
      for (Map.Entry<String, ?> entry : headers.entrySet()) {
        // Don't include internal Booster headers in HTTP response
        if (entry.getKey().startsWith("X-Booster-")) {
          continue;
        }
        Object value = entry.getValue();
        responseHeaders.put(entry.getKey(), value instanceof List<?> list
            ? list.stream().map(item -> item == null ? "" : JsValues.jsString(item)).collect(Collectors.joining(","))
            : (value == null ? "undefined" : JsValues.jsString(value)));
      }
    }
    return new AzureHttpResponse(responseHeaders, 200, JsValues.truthy(body) ? body : null);
  }

  /**
   * {@code requestFailed}: status {@code httpStatusCodeFor(error)}, body {@code { status, title, reason }}.
   *
   * @param error the error
   * @return the response
   */
  public static AzureHttpResponse requestFailed(Throwable error) {
    int status = BoosterErrors.httpStatusCodeFor(error);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", status);
    body.put("title", TypeLevel.toClassTitle(error));
    body.put("reason", error.getMessage() == null ? "" : error.getMessage());
    return new AzureHttpResponse(defaultHeaders(), status, body);
  }

  /**
   * {@code healthRequestResult}: status 200 when healthy, 503 otherwise, the health tree as body.
   *
   * @param body the health tree
   * @param isHealthy whether every component is up
   * @return the response
   */
  public static AzureHttpResponse healthRequestResult(Object body, boolean isHealthy) {
    return new AzureHttpResponse(defaultHeaders(), isHealthy ? 200 : 503, body);
  }

  private static Map<String, String> defaultHeaders() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Access-Control-Allow-Origin", "*");
    headers.put("Content-Type", "application/json");
    return headers;
  }
}
