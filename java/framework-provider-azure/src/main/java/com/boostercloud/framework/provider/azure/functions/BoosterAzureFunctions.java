package com.boostercloud.framework.provider.azure.functions;

import com.boostercloud.framework.provider.azure.helpers.JsValues;
import com.boostercloud.framework.provider.azure.library.ScheduledAdapter;
import com.boostercloud.framework.provider.azure.library.SubscriptionModel;
import com.boostercloud.framework.provider.azure.types.AzureCosmosDBFunctionInput;
import com.boostercloud.framework.provider.azure.types.AzureEventHubFunctionInput;
import com.boostercloud.framework.provider.azure.types.AzureHttpFunctionInput;
import com.boostercloud.framework.provider.azure.types.AzureHttpRequest;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.provider.azure.types.AzureWebPubSubFunctionInput;
import com.boostercloud.framework.provider.azure.types.TimerInfo;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.UserApp;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.CustomBinding;
import com.microsoft.azure.functions.annotation.EventHubTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * The Azure Functions (Java worker) entry points of a Booster application: one method per function the Azure
 * infrastructure deploys, the Java counterpart of the generated {@code functions.js}. Each method wraps the trigger
 * input exactly as {@code functions.js} does ({@code { request, context }}, {@code { documents, context }}, ...), boots
 * the application once per worker ({@link BoosterAzureApplication}) and forwards to the matching {@link UserApp}
 * entry point; HTTP and Web PubSub results are converted to the worker's return types.
 *
 * <p>The {@code function.json} files are written by the infrastructure module; they reference these methods through
 * {@code "entryPoint": "<class>.<method>"} (see the constants below) and must use the binding names given here
 * ({@code request}, {@code documents}, {@code messages}, {@code timer}, {@code blob}). Several functions share one
 * method: every {@code <ReadModel>-subscriptions-notifier} uses {@link #subscriptionsNotifier} and every
 * {@code scheduleFunction-<Command>} uses {@link #scheduledCommand}; the read model / command name is taken from the
 * function name, as {@code functions.js} hard-codes it in {@code typeName}. The trigger attributes in the annotations
 * (database, container, schedule...) are placeholders; the generated {@code function.json} is authoritative.
 */
public class BoosterAzureFunctions {

  /** Fully qualified name of this class (the {@code entryPoint} prefix). */
  public static final String CLASS_NAME = "com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions";

  /** Function {@code graphql} (HTTP POST {@code /api/graphql}). */
  public static final String GRAPHQL_FUNCTION = "graphql";
  /** Function {@code sensor-health} (HTTP GET {@code /api/sensor/health/{*componentPath}}). */
  public static final String SENSOR_HEALTH_FUNCTION = "sensor-health";
  /** Function {@code eventHandler} (events store change feed, event streaming off). */
  public static final String EVENT_HANDLER_FUNCTION = "eventHandler";
  /** Function {@code eventProducer} (events store change feed, event streaming on). */
  public static final String EVENT_PRODUCER_FUNCTION = "eventProducer";
  /** Function {@code eventConsumer} (Event Hub trigger, consumer function app). */
  public static final String EVENT_CONSUMER_FUNCTION = "eventConsumer";
  /** Function {@code connect} (Web PubSub system/connect). */
  public static final String CONNECT_FUNCTION = "connect";
  /** Function {@code disconnect} (Web PubSub system/disconnect). */
  public static final String DISCONNECT_FUNCTION = "disconnect";
  /** Function {@code messages} (Web PubSub user/message). */
  public static final String MESSAGES_FUNCTION = "messages";
  /** Suffix of the per-read-model notifier functions ({@code <ReadModel>-subscriptions-notifier}). */
  public static final String SUBSCRIPTIONS_NOTIFIER_SUFFIX = "-subscriptions-notifier";
  /** Prefix of the per-command timer functions ({@code scheduleFunction-<Command>}). */
  public static final String SCHEDULED_FUNCTION_PREFIX = "scheduleFunction-";

  /** {@code entryPoint} of {@code graphql}. */
  public static final String GRAPHQL_ENTRY_POINT = CLASS_NAME + ".graphql";
  /** {@code entryPoint} of {@code sensor-health}. */
  public static final String SENSOR_HEALTH_ENTRY_POINT = CLASS_NAME + ".sensorHealth";
  /** {@code entryPoint} of {@code eventHandler}. */
  public static final String EVENT_HANDLER_ENTRY_POINT = CLASS_NAME + ".eventHandler";
  /** {@code entryPoint} of {@code eventProducer}. */
  public static final String EVENT_PRODUCER_ENTRY_POINT = CLASS_NAME + ".eventProducer";
  /** {@code entryPoint} of {@code eventConsumer}. */
  public static final String EVENT_CONSUMER_ENTRY_POINT = CLASS_NAME + ".eventConsumer";
  /** {@code entryPoint} of {@code connect}. */
  public static final String CONNECT_ENTRY_POINT = CLASS_NAME + ".connect";
  /** {@code entryPoint} of {@code disconnect}. */
  public static final String DISCONNECT_ENTRY_POINT = CLASS_NAME + ".disconnect";
  /** {@code entryPoint} of {@code messages}. */
  public static final String MESSAGES_ENTRY_POINT = CLASS_NAME + ".messages";
  /** {@code entryPoint} of every {@code <ReadModel>-subscriptions-notifier}. */
  public static final String SUBSCRIPTIONS_NOTIFIER_ENTRY_POINT = CLASS_NAME + ".subscriptionsNotifier";
  /** {@code entryPoint} of every {@code scheduleFunction-<Command>}. */
  public static final String SCHEDULED_COMMAND_ENTRY_POINT = CLASS_NAME + ".scheduledCommand";
  /** {@code entryPoint} of HTTP rocket functions (the webhook rocket's request shape). */
  public static final String ROCKET_HTTP_ENTRY_POINT = CLASS_NAME + ".rocketHttp";
  /** {@code entryPoint} of blob-triggered rocket functions (the file-uploads rocket's {@code { blob, context }}). */
  public static final String ROCKET_BLOB_ENTRY_POINT = CLASS_NAME + ".rocketBlob";

  /** Every fixed function name with its entry point (per-read-model and per-command functions excluded). */
  public static final Map<String, String> ENTRY_POINTS = Map.of(
      GRAPHQL_FUNCTION, GRAPHQL_ENTRY_POINT,
      SENSOR_HEALTH_FUNCTION, SENSOR_HEALTH_ENTRY_POINT,
      EVENT_HANDLER_FUNCTION, EVENT_HANDLER_ENTRY_POINT,
      EVENT_PRODUCER_FUNCTION, EVENT_PRODUCER_ENTRY_POINT,
      EVENT_CONSUMER_FUNCTION, EVENT_CONSUMER_ENTRY_POINT,
      CONNECT_FUNCTION, CONNECT_ENTRY_POINT,
      DISCONNECT_FUNCTION, DISCONNECT_ENTRY_POINT,
      MESSAGES_FUNCTION, MESSAGES_ENTRY_POINT);

  private static final Pattern MULTIPART = Pattern.compile("multipart/form-data", Pattern.CASE_INSENSITIVE);

  /** Creates the function class (the worker instantiates it). */
  public BoosterAzureFunctions() {}

  /**
   * {@code graphql}: {@code boosterServeGraphQL({ request, context })}.
   *
   * @param request the HTTP request (binding {@code request})
   * @param context the invocation context
   * @return the HTTP response
   */
  @FunctionName(GRAPHQL_FUNCTION)
  public HttpResponseMessage graphql(
      @HttpTrigger(name = "request", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
          HttpRequestMessage<Optional<String>> request,
      final ExecutionContext context) {
    AzureHttpFunctionInput input =
        new AzureHttpFunctionInput(FunctionMappings.httpRequest(request), FunctionMappings.context(context, null));
    return FunctionMappings.httpResponse(request, userApp().boosterServeGraphQL(input));
  }

  /**
   * {@code sensor-health}: {@code boosterHealth({ request, context })}.
   *
   * @param request the HTTP request (binding {@code request})
   * @param context the invocation context
   * @return the HTTP response
   */
  @FunctionName(SENSOR_HEALTH_FUNCTION)
  public HttpResponseMessage sensorHealth(
      @HttpTrigger(name = "request", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS,
          route = "sensor/health/{*componentPath}") HttpRequestMessage<Optional<String>> request,
      final ExecutionContext context) {
    AzureHttpFunctionInput input =
        new AzureHttpFunctionInput(FunctionMappings.httpRequest(request), FunctionMappings.context(context, null));
    return FunctionMappings.httpResponse(request, userApp().boosterHealth(input));
  }

  /**
   * {@code eventHandler}: {@code boosterEventDispatcher({ documents, context })}.
   *
   * @param documents the changed documents as a JSON array (binding {@code documents})
   * @param context the invocation context
   */
  @FunctionName(EVENT_HANDLER_FUNCTION)
  public void eventHandler(
      @CosmosDBTrigger(name = "documents", databaseName = "%BOOSTER_COSMOS_DATABASE%",
          containerName = "%BOOSTER_EVENTS_CONTAINER%", connection = "COSMOSDB_CONNECTION_STRING",
          leaseContainerName = "leases", createLeaseContainerIfNotExists = true) String documents,
      final ExecutionContext context) {
    userApp().boosterEventDispatcher(
        new AzureCosmosDBFunctionInput(FunctionMappings.documents(documents), FunctionMappings.context(context, null)));
  }

  /**
   * {@code eventProducer}: {@code boosterProduceEventStream({ documents, context })}.
   *
   * @param documents the changed documents as a JSON array (binding {@code documents})
   * @param context the invocation context
   */
  @FunctionName(EVENT_PRODUCER_FUNCTION)
  public void eventProducer(
      @CosmosDBTrigger(name = "documents", databaseName = "%BOOSTER_COSMOS_DATABASE%",
          containerName = "%BOOSTER_EVENTS_CONTAINER%", connection = "COSMOSDB_CONNECTION_STRING",
          leaseContainerName = "leases", createLeaseContainerIfNotExists = true) String documents,
      final ExecutionContext context) {
    userApp().boosterProduceEventStream(
        new AzureCosmosDBFunctionInput(FunctionMappings.documents(documents), FunctionMappings.context(context, null)));
  }

  /**
   * {@code eventConsumer}: {@code boosterConsumeEventStream({ messages, context })} (cardinality many).
   *
   * @param messages the message bodies (binding {@code messages})
   * @param context the invocation context
   */
  @FunctionName(EVENT_CONSUMER_FUNCTION)
  public void eventConsumer(
      @EventHubTrigger(name = "messages", eventHubName = "%EVENTHUB_NAME%", connection = "EVENTHUB_CONNECTION_STRING",
          cardinality = Cardinality.MANY, dataType = "string") List<String> messages,
      final ExecutionContext context) {
    List<Object> bodies = messages == null ? List.of() : List.copyOf(messages);
    userApp().boosterConsumeEventStream(
        new AzureEventHubFunctionInput(new java.util.ArrayList<>(bodies), FunctionMappings.context(context, null)));
  }

  /**
   * Every {@code <ReadModel>-subscriptions-notifier}: {@code boosterNotifySubscribers({ documents, context, typeName })}
   * with {@code typeName} taken from the function name.
   *
   * @param documents the changed read models as a JSON array (binding {@code documents})
   * @param context the invocation context
   */
  @FunctionName("ReadModel" + SUBSCRIPTIONS_NOTIFIER_SUFFIX)
  public void subscriptionsNotifier(
      @CosmosDBTrigger(name = "documents", databaseName = "%BOOSTER_COSMOS_DATABASE%",
          containerName = "%BOOSTER_READ_MODEL_CONTAINER%", connection = "COSMOSDB_CONNECTION_STRING",
          leaseContainerName = "leases", createLeaseContainerIfNotExists = true) String documents,
      final ExecutionContext context) {
    String typeName = readModelNameOf(context.getFunctionName());
    userApp().boosterNotifySubscribers(new SubscriptionModel.SubscriptionNotifierInput(
        FunctionMappings.documents(documents), FunctionMappings.context(context, null), typeName));
  }

  /**
   * Every {@code scheduleFunction-<Command>}: {@code boosterTriggerScheduledCommands({ timer, context, typeName })} with
   * {@code typeName} taken from the function name.
   *
   * @param timer the timer information JSON (binding {@code timer})
   * @param context the invocation context
   */
  @FunctionName(SCHEDULED_FUNCTION_PREFIX + "Command")
  public void scheduledCommand(
      @TimerTrigger(name = "timer", schedule = "0 * * * * *") String timer, final ExecutionContext context) {
    String typeName = commandNameOf(context.getFunctionName());
    userApp().boosterTriggerScheduledCommands(new ScheduledAdapter.ScheduledCommandInput(
        TimerInfo.fromJson(timer), FunctionMappings.context(context, null), typeName));
  }

  /**
   * {@code connect}: {@code boosterServeGraphQL({ request, context })}; returns {@code { subprotocol }}, or
   * {@code { code: 'InternalError', errorMessage }} when the application fails.
   *
   * @param request the Web PubSub trigger value (binding {@code request})
   * @param connectionContext the {@code connectionContext} trigger metadata
   * @param context the invocation context
   * @return the connect response JSON
   */
  @FunctionName(CONNECT_FUNCTION)
  public String connect(
      @CustomBinding(name = "request", type = "webPubSubTrigger", direction = "in") String request,
      @BindingName("connectionContext") String connectionContext,
      final ExecutionContext context) {
    try {
      return FunctionMappings.webPubSubResponse(userApp().boosterServeGraphQL(webPubSubInput(request, connectionContext,
          context)));
    } catch (RuntimeException error) {
      log(context, "WebPubSub CONNECT error:", error);
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("code", "InternalError");
      response.put("errorMessage", messageOr(error));
      return FunctionMappings.webPubSubResponse(response);
    }
  }

  /**
   * {@code disconnect}: {@code boosterServeGraphQL({ request, context })}; failures are logged and answered with
   * nothing.
   *
   * @param request the Web PubSub trigger value (binding {@code request})
   * @param connectionContext the {@code connectionContext} trigger metadata
   * @param context the invocation context
   * @return {@code null}, or the application's response JSON
   */
  @FunctionName(DISCONNECT_FUNCTION)
  public String disconnect(
      @CustomBinding(name = "request", type = "webPubSubTrigger", direction = "in") String request,
      @BindingName("connectionContext") String connectionContext,
      final ExecutionContext context) {
    try {
      return FunctionMappings.webPubSubResponse(userApp().boosterServeGraphQL(webPubSubInput(request, connectionContext,
          context)));
    } catch (RuntimeException error) {
      log(context, "WebPubSub DISCONNECT error:", error);
      return null;
    }
  }

  /**
   * {@code messages}: {@code boosterServeGraphQL({ request, context })}; returns {@code { data, dataType: 'json' }}, or
   * {@code { data: { errors: [{ message }] }, dataType: 'json' }} when the application fails.
   *
   * @param request the Web PubSub trigger value (binding {@code request})
   * @param connectionContext the {@code connectionContext} trigger metadata
   * @param context the invocation context
   * @return the message response JSON
   */
  @FunctionName(MESSAGES_FUNCTION)
  public String messages(
      @CustomBinding(name = "request", type = "webPubSubTrigger", direction = "in") String request,
      @BindingName("connectionContext") String connectionContext,
      final ExecutionContext context) {
    try {
      return FunctionMappings.webPubSubResponse(userApp().boosterServeGraphQL(webPubSubInput(request, connectionContext,
          context)));
    } catch (RuntimeException error) {
      log(context, "WebPubSub MESSAGE error:", error);
      Map<String, Object> message = new LinkedHashMap<>();
      message.put("message", messageOr(error));
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("errors", List.of(message));
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("data", data);
      response.put("dataType", "json");
      return FunctionMappings.webPubSubResponse(response);
    }
  }

  /**
   * HTTP rocket functions: builds the request the webhook rocket's generated handler builds
   * ({@code { req: { method, url, originalUrl, headers, query, params, rawBody, body } }}, the body parsed as JSON
   * when possible, raw bytes for {@code multipart/form-data}), calls {@code boosterRocketDispatcher} and maps the
   * result's {@code { status, headers, body }} (object bodies serialised as JSON).
   *
   * @param request the HTTP request (binding {@code request})
   * @param context the invocation context
   * @return the HTTP response
   */
  @FunctionName("rocket-http")
  public HttpResponseMessage rocketHttp(
      @HttpTrigger(name = "request", methods = {HttpMethod.GET, HttpMethod.POST},
          authLevel = AuthorizationLevel.ANONYMOUS, route = "{*route}") HttpRequestMessage<Optional<String>> request,
      final ExecutionContext context) {
    AzureHttpRequest httpRequest = FunctionMappings.httpRequest(request);
    Map<String, String> headers = new LinkedHashMap<>();
    httpRequest.forEachHeader((value, key) -> headers.put(key, value));
    String contentType = headers.getOrDefault("content-type", "");
    String rawBody = httpRequest.text();
    Object body;
    if (MULTIPART.matcher(contentType).find()) {
      body = rawBody.getBytes(StandardCharsets.UTF_8);
    } else {
      try {
        body = BoosterJson.parse(rawBody);
      } catch (RuntimeException notJson) {
        body = rawBody;
      }
    }
    Map<String, Object> req = new LinkedHashMap<>();
    req.put("method", httpRequest.method());
    req.put("url", httpRequest.url());
    req.put("originalUrl", httpRequest.url());
    req.put("headers", headers);
    req.put("query", httpRequest.query());
    req.put("params", httpRequest.params());
    req.put("rawBody", rawBody);
    req.put("body", body);
    Map<String, Object> webhookRequest = new LinkedHashMap<>();
    webhookRequest.put("req", req);

    Object result = userApp().boosterRocketDispatcher(webhookRequest);

    Object status = JsValues.get(result, "status");
    HttpResponseMessage.Builder builder = request.createResponseBuilder(
        HttpStatusType.custom(status instanceof Number number ? number.intValue() : 200));
    if (JsValues.get(result, "headers") instanceof Map<?, ?> resultHeaders) {
      resultHeaders.forEach((key, value) -> builder.header(String.valueOf(key), JsValues.jsString(value)));
    }
    Object responseBody = JsValues.get(result, "body");
    if (responseBody instanceof byte[] || responseBody instanceof String) {
      builder.body(responseBody);
    } else if (responseBody != null) {
      builder.body(BoosterJson.toJson(responseBody));
    }
    return builder.build();
  }

  /**
   * Blob-triggered rocket functions: {@code boosterRocketDispatcher({ blob, context })}, with the blob path in
   * {@code context.triggerMetadata.blobTrigger} (what the file-uploads rocket reads).
   *
   * @param blob the blob content (binding {@code blob})
   * @param blobTrigger the blob path ({@code <container>/<name>})
   * @param uri the blob URI
   * @param context the invocation context
   */
  @FunctionName("rocket-blob")
  public void rocketBlob(
      @BlobTrigger(name = "blob", path = "{blobPath}", dataType = "binary") byte[] blob,
      @BindingName("BlobTrigger") String blobTrigger,
      @BindingName("Uri") String uri,
      final ExecutionContext context) {
    Map<String, Object> triggerMetadata = new LinkedHashMap<>();
    triggerMetadata.put("blobTrigger", blobTrigger);
    triggerMetadata.put("uri", uri);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("blob", blob);
    input.put("context", FunctionMappings.context(context, triggerMetadata));
    userApp().boosterRocketDispatcher(input);
  }

  /**
   * @param functionName {@code <ReadModel>-subscriptions-notifier}
   * @return the read model name
   */
  static String readModelNameOf(String functionName) {
    if (functionName != null && functionName.endsWith(SUBSCRIPTIONS_NOTIFIER_SUFFIX)) {
      return functionName.substring(0, functionName.length() - SUBSCRIPTIONS_NOTIFIER_SUFFIX.length());
    }
    return functionName;
  }

  /**
   * @param functionName {@code scheduleFunction-<Command>}
   * @return the command name
   */
  static String commandNameOf(String functionName) {
    if (functionName != null && functionName.startsWith(SCHEDULED_FUNCTION_PREFIX)) {
      return functionName.substring(SCHEDULED_FUNCTION_PREFIX.length());
    }
    return functionName;
  }

  private static AzureWebPubSubFunctionInput webPubSubInput(
      String request, String connectionContext, ExecutionContext context) {
    AzureInvocationContext invocationContext =
        FunctionMappings.context(context, FunctionMappings.webPubSubTriggerMetadata(connectionContext));
    return new AzureWebPubSubFunctionInput(FunctionMappings.webPubSubRequest(request), invocationContext);
  }

  private static String messageOr(Throwable error) {
    String message = error.getMessage();
    return message == null || message.isEmpty() ? "Internal server error" : message;
  }

  private static void log(ExecutionContext context, String message, Throwable error) {
    if (context != null && context.getLogger() != null) {
      context.getLogger().log(Level.SEVERE, message + " " + error, error);
    }
  }

  /**
   * @return the application (booted on first use)
   */
  protected UserApp userApp() {
    return BoosterAzureApplication.userApp();
  }
}
