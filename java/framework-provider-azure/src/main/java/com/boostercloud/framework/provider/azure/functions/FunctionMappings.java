package com.boostercloud.framework.provider.azure.functions;

import com.boostercloud.framework.provider.azure.library.AzureHttpResponse;
import com.boostercloud.framework.provider.azure.library.WebPubSubConnectResponse;
import com.boostercloud.framework.provider.azure.library.WebPubSubMessageResponse;
import com.boostercloud.framework.provider.azure.types.AzureHttpRequest;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.provider.azure.types.AzureWebPubSubRequest;
import com.boostercloud.framework.provider.azure.types.WebPubSubConnectionContext;
import com.boostercloud.framework.types.BoosterJson;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatusType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Conversions between the Azure Functions Java worker types and the input wrappers / return values of the adapters
 * (what the generated {@code functions.js} does implicitly in TypeScript).
 */
public final class FunctionMappings {

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
