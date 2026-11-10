package com.boostercloud.framework.provider.azure.functions;

import com.boostercloud.framework.provider.azure.library.AzureHttpResponse;
import com.boostercloud.framework.provider.azure.library.WebPubSubConnectResponse;
import com.boostercloud.framework.provider.azure.library.WebPubSubMessageResponse;
import com.boostercloud.framework.provider.azure.types.AzureHttpRequest;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.provider.azure.types.AzureWebPubSubRequest;
import com.boostercloud.framework.provider.azure.types.WebPubSubConnectionContext;
import com.boostercloud.framework.types.BoosterJson;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatusType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Conversions between the Azure Functions Java worker types and the input wrappers / return values of the adapters
 * (what the generated {@code functions.js} does implicitly in TypeScript).
 */
public final class FunctionMappings {

  private static final Pattern MULTIPART = Pattern.compile("multipart/form-data", Pattern.CASE_INSENSITIVE);

  private FunctionMappings() {}

  /**
   * @param context the worker context, or {@code null}
   * @param triggerMetadata trigger metadata to expose as {@code context.triggerMetadata}
   * @return the invocation context
   */
  public static AzureInvocationContext context(ExecutionContext context, Map<String, Object> triggerMetadata) {
    if (context == null) {
      return new AzureInvocationContext(null, null, triggerMetadata, null);
    }
    return new AzureInvocationContext(context.getInvocationId(), context.getFunctionName(), triggerMetadata, context);
  }

  /**
   * @param request the worker request
   * @return the Fetch-like request of the adapters
   */
  public static AzureHttpRequest httpRequest(HttpRequestMessage<Optional<String>> request) {
    String method = request.getHttpMethod() == null ? null : request.getHttpMethod().name();
    String url = request.getUri() == null ? null : request.getUri().toString();
    Optional<String> body = request.getBody();
    return new AzureHttpRequest(method, url, request.getHeaders(), body == null ? null : body.orElse(null),
        request.getQueryParameters(), null);
  }

  /**
   * The rocket request the webhook rocket's generated Node handler ({@code functions.js} of
   * {@code @boostercloud/rocket-webhook-azure-infrastructure}) builds from a Functions HTTP request:
   * {@code { req: { method, url, originalUrl, headers, query, params, rawBody, body } }} with
   *
   * <ul>
   *   <li>{@code headers}: {@code request.headers.forEach((value, key) => headers[key] = value)}, i.e. lower-cased
   *       names in sorted order (Fetch {@code Headers} iteration), repeated values joined with {@code ", "};</li>
   *   <li>{@code query}: {@code new URL(request.url).searchParams.forEach(...)}: form-urlencoded decoding of the URL's
   *       query, the last value of a repeated name wins;</li>
   *   <li>{@code multipart/form-data} bodies: {@code rawBody} = the bytes decoded as UTF-8
   *       ({@code buffer.toString()}), {@code body} = the bytes;</li>
   *   <li>other bodies: {@code rawBody} = {@code await request.text()} (UTF-8, a leading BOM dropped), {@code body} =
   *       {@code JSON.parse(rawBody)}, or {@code rawBody} itself when it is not JSON ({@code ""} without body);</li>
   *   <li>{@code params}: {@code {}} (webhook routes are literal).</li>
   * </ul>
   *
   * @param request the worker request (body bound as bytes)
   * @return the rocket request
   */
  public static Map<String, Object> rocketHttpRequest(HttpRequestMessage<Optional<byte[]>> request) {
    String method = request.getHttpMethod() == null ? null : request.getHttpMethod().name();
    String url = request.getUri() == null ? null : request.getUri().toString();
    Map<String, String> sortedHeaders = new TreeMap<>();
    if (request.getHeaders() != null) {
      request.getHeaders().forEach((key, value) -> {
        if (key != null && value != null) {
          sortedHeaders.merge(key.toLowerCase(Locale.ROOT), value, (first, second) -> first + ", " + second);
        }
      });
    }
    Map<String, String> headers = new LinkedHashMap<>(sortedHeaders);
    Optional<byte[]> body = request.getBody();
    byte[] bytes = body == null ? new byte[0] : body.orElse(new byte[0]);
    String contentType = headers.getOrDefault("content-type", "");
    String rawBody;
    Object parsedBody;
    if (MULTIPART.matcher(contentType).find()) {
      rawBody = new String(bytes, StandardCharsets.UTF_8);
      parsedBody = bytes;
    } else {
      rawBody = utf8DecodeWithoutBom(bytes);
      parsedBody = jsonParseOrText(rawBody);
    }
    Map<String, Object> req = new LinkedHashMap<>();
    req.put("method", method);
    req.put("url", url);
    req.put("originalUrl", url);
    req.put("headers", headers);
    req.put("query", searchParams(request.getUri() == null ? null : request.getUri().getRawQuery()));
    req.put("params", new LinkedHashMap<String, String>());
    req.put("rawBody", rawBody);
    req.put("body", parsedBody);
    Map<String, Object> webhookRequest = new LinkedHashMap<>();
    webhookRequest.put("req", req);
    return webhookRequest;
  }

  /**
   * {@code url.searchParams.forEach((value, key) => query[key] = value)}: the {@code application/x-www-form-urlencoded}
   * parsing of {@code URLSearchParams} ({@code +} is a space, percent escapes are UTF-8 bytes, invalid escapes are
   * kept as they are), the last value of a repeated name winning.
   *
   * @param rawQuery the query of the URL without {@code ?}, or {@code null}
   * @return the parameters, in order of first appearance
   */
  static Map<String, String> searchParams(String rawQuery) {
    Map<String, String> query = new LinkedHashMap<>();
    if (rawQuery == null || rawQuery.isEmpty()) {
      return query;
    }
    for (String sequence : rawQuery.split("&", -1)) {
      if (sequence.isEmpty()) {
        continue;
      }
      int equals = sequence.indexOf('=');
      String name = equals < 0 ? sequence : sequence.substring(0, equals);
      String value = equals < 0 ? "" : sequence.substring(equals + 1);
      query.put(percentDecode(name.replace('+', ' ')), percentDecode(value.replace('+', ' ')));
    }
    return query;
  }

  private static String percentDecode(String text) {
    byte[] input = text.getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
    for (int i = 0; i < input.length; i++) {
      byte current = input[i];
      if (current == '%' && i + 2 < input.length && isHex(input[i + 1]) && isHex(input[i + 2])) {
        output.write(Character.digit(input[i + 1], 16) * 16 + Character.digit(input[i + 2], 16));
        i += 2;
      } else {
        output.write(current);
      }
    }
    return output.toString(StandardCharsets.UTF_8);
  }

  private static boolean isHex(byte value) {
    return Character.digit(value, 16) >= 0;
  }

  private static String utf8DecodeWithoutBom(byte[] bytes) {
    String text = new String(bytes, StandardCharsets.UTF_8);
    return text.startsWith("\uFEFF") ? text.substring(1) : text;
  }

  /**
   * {@code try { JSON.parse(text) } catch { text }}: strict JSON (no trailing content).
   *
   * @param text the body text
   * @return the parsed raw value, or the text
   */
  static Object jsonParseOrText(String text) {
    try {
      return BoosterJson.mapper().readerFor(Object.class)
          .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .readValue(text);
    } catch (IOException | RuntimeException notJson) {
      return text;
    }
  }

  /**
   * Parses the JSON array a Cosmos DB trigger passes to a {@code String} parameter.
   *
   * @param documents the JSON array, or {@code null}
   * @return the documents as raw maps
   */
  public static List<Object> documents(String documents) {
    if (documents == null || documents.isBlank()) {
      return new ArrayList<>();
    }
    Object parsed = BoosterJson.parse(documents);
    if (parsed instanceof List<?> list) {
      return new ArrayList<>(list);
    }
    List<Object> single = new ArrayList<>();
    single.add(parsed);
    return single;
  }

  /**
   * Builds the Web PubSub request from the trigger value (a JSON object with {@code connectionContext},
   * {@code data}, {@code dataType}, or the bare data) and the {@code connectionContext} trigger metadata.
   *
   * @param request the trigger value, or {@code null}
   * @return the request
   */
  public static AzureWebPubSubRequest webPubSubRequest(String request) {
    if (request == null) {
      return new AzureWebPubSubRequest(null, null, null);
    }
    Object parsed;
    try {
      parsed = BoosterJson.parse(request);
    } catch (RuntimeException notJson) {
      return new AzureWebPubSubRequest(null, request, "text");
    }
    if (parsed instanceof Map<?, ?> map
        && (map.containsKey("connectionContext") || map.containsKey("data") || map.containsKey("dataType"))) {
      WebPubSubConnectionContext connectionContext = map.get("connectionContext") == null
          ? null
          : BoosterJson.convert(map.get("connectionContext"), WebPubSubConnectionContext.class);
      return new AzureWebPubSubRequest(connectionContext, map.get("data"),
          map.get("dataType") == null ? null : map.get("dataType").toString());
    }
    return new AzureWebPubSubRequest(null, request, null);
  }

  /**
   * @param connectionContext the {@code connectionContext} trigger metadata (JSON), or {@code null}
   * @return the trigger metadata map
   */
  public static Map<String, Object> webPubSubTriggerMetadata(String connectionContext) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    if (connectionContext != null && !connectionContext.isBlank()) {
      try {
        metadata.put("connectionContext", BoosterJson.parse(connectionContext));
      } catch (RuntimeException ignored) {
        // Not JSON: no connection context in the metadata
      }
    }
    return metadata;
  }

  /**
   * Turns the result of an HTTP entry point ({@link AzureHttpResponse} or anything else) into the worker response:
   * {@code jsonBody} is serialised with {@code BoosterJson}; other results are a 200 JSON response; {@code null} is an
   * empty 200 response.
   *
   * @param request the worker request
   * @param result the entry point result
   * @return the worker response
   */
  public static HttpResponseMessage httpResponse(HttpRequestMessage<?> request, Object result) {
    if (result instanceof AzureHttpResponse response) {
      HttpResponseMessage.Builder builder = request.createResponseBuilder(HttpStatusType.custom(response.status()));
      if (response.headers() != null) {
        response.headers().forEach(builder::header);
      }
      if (response.jsonBody() != null) {
        builder.body(BoosterJson.toJson(response.jsonBody()));
      }
      return builder.build();
    }
    HttpResponseMessage.Builder builder = request.createResponseBuilder(HttpStatusType.custom(200));
    if (result != null) {
      builder.header("Content-Type", "application/json").body(BoosterJson.toJson(result));
    }
    return builder.build();
  }

  /**
   * @param result a {@link WebPubSubConnectResponse}, {@link WebPubSubMessageResponse}, an error map or {@code null}
   * @return the JSON returned to the Web PubSub extension, or {@code null}
   */
  public static String webPubSubResponse(Object result) {
    return result == null ? null : BoosterJson.toJson(result);
  }
}
