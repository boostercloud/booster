package com.boostercloud.framework.provider.azure.infrastructure.helper;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.Constants;
import com.boostercloud.framework.provider.azure.Constants.EnvironmentVarNames;
import com.boostercloud.framework.provider.azure.Constants.OtherEnvironmentVarNames;
import com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions;
import com.boostercloud.framework.provider.azure.infrastructure.functions.ScheduledFunctions;
import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.ResourceNames;
import com.boostercloud.framework.types.ScheduleInterface;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code helper/functions-code-generator.ts}. TypeScript generates a {@code functions.js} that registers every
 * function with the Node v4 programming model; a Java function app declares each function with a
 * {@code function.json}, so this class produces those definitions instead: the same function names, triggers, routes,
 * authorization levels, Cosmos DB databases/containers/lease container, Web PubSub hub and events, connection setting
 * names and NCRONTAB schedules, bound to the entry points of {@link BoosterAzureFunctions} (which wrap the inputs as
 * {@code functions.js} does: {@code { request, context }}, {@code { documents, context }}, ...).
 *
 * <table>
 *   <caption>Main function app</caption>
 *   <tr><th>Function</th><th>Trigger</th><th>Entry point</th></tr>
 *   <tr><td>{@code graphql}</td><td>HTTP POST, anonymous</td><td>{@code graphql}</td></tr>
 *   <tr><td>{@code sensor-health}</td><td>HTTP GET {@code sensor/health/{*componentPath}}</td>
 *       <td>{@code sensorHealth}</td></tr>
 *   <tr><td>{@code eventHandler} (streaming off) / {@code eventProducer} (streaming on)</td>
 *       <td>Cosmos DB events store, lease container {@code leases}</td>
 *       <td>{@code eventHandler} / {@code eventProducer}</td></tr>
 *   <tr><td>{@code connect}, {@code disconnect}, {@code messages} (subscriptions)</td>
 *       <td>Web PubSub hub {@code booster}</td><td>{@code connect}, {@code disconnect}, {@code messages}</td></tr>
 *   <tr><td>{@code <ReadModel>-subscriptions-notifier} (subscriptions)</td><td>Cosmos DB read model container</td>
 *       <td>{@code subscriptionsNotifier}</td></tr>
 *   <tr><td>{@code scheduleFunction-<Command>}</td><td>timer</td><td>{@code scheduledCommand}</td></tr>
 * </table>
 *
 * <p>The consumer function app has {@code eventConsumer} (Event Hub, cardinality many). Values are inserted verbatim,
 * as TypeScript does; the {@code function.json} files are JSON, so no escaping problem arises.
 */
public final class FunctionsCodeGenerator {

  /** The trigger binding name of the HTTP and Web PubSub entry points. */
  public static final String REQUEST_BINDING = "request";
  /** The trigger binding name of the Cosmos DB entry points. */
  public static final String DOCUMENTS_BINDING = "documents";
  /** The trigger binding name of the Event Hub entry point. */
  public static final String MESSAGES_BINDING = "messages";
  /** The trigger binding name of the timer entry point. */
  public static final String TIMER_BINDING = "timer";
  /** The lease container shared by every Cosmos DB trigger. */
  public static final String LEASE_CONTAINER = "leases";

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public FunctionsCodeGenerator(BoosterConfig config) {
    this.config = config;
  }

  /**
   * {@code generateFunctionsCode()}: the functions of the main function app, in the TypeScript order (GraphQL, health,
   * event handler or producer, then with subscriptions the three Web PubSub functions and one notifier per read model,
   * then one timer per scheduled command with a non-empty schedule).
   *
   * @return the definitions
   */
  public List<FunctionDefinition> generateFunctionsCode() {
    Logger logger = Loggers.getLogger(config, "FunctionsCodeGenerator#generateFunctionsCode");
    logger.info("Generating Azure Functions v4 registration code");
    List<FunctionDefinition> functions = new ArrayList<>();
    functions.add(generateGraphQLFunction());
    functions.add(generateHealthFunction());
    if (config.eventStreamConfiguration.enabled()) {
      functions.add(generateEventStreamProducerFunction());
    } else {
      functions.add(generateEventHandlerFunction());
    }
    if (config.enableSubscriptions) {
      functions.add(webPubSubFunction(BoosterAzureFunctions.CONNECT_FUNCTION, "system", "connect",
          BoosterAzureFunctions.CONNECT_ENTRY_POINT));
      functions.add(webPubSubFunction(BoosterAzureFunctions.DISCONNECT_FUNCTION, "system", "disconnect",
          BoosterAzureFunctions.DISCONNECT_ENTRY_POINT));
      functions.add(webPubSubFunction(BoosterAzureFunctions.MESSAGES_FUNCTION, "user", "message",
          BoosterAzureFunctions.MESSAGES_ENTRY_POINT));
      functions.addAll(generateSubscriptionsNotifierFunctions());
    }
    functions.addAll(generateScheduledFunctions());
    logger.info("Azure Functions v4 registration code generated");
    return functions;
  }

  /**
   * {@code generateConsumerFunctionsCode()}: the functions of the event stream consumer function app.
   *
   * @return {@code [eventConsumer]}, or an empty list when event streaming is disabled (TypeScript {@code ''})
   */
  public List<FunctionDefinition> generateConsumerFunctionsCode() {
    if (!config.eventStreamConfiguration.enabled()) {
      return List.of();
    }
    Logger logger = Loggers.getLogger(config, "FunctionsCodeGenerator#generateConsumerFunctionsCode");
    logger.info("Generating Azure Consumer Functions v4 registration code");
    String streamTopic = config.resourceNames().streamTopic();
    FunctionDefinition consumer = function(BoosterAzureFunctions.EVENT_CONSUMER_FUNCTION,
        BoosterAzureFunctions.EVENT_CONSUMER_ENTRY_POINT,
        Binding.eventHubTrigger(MESSAGES_BINDING, EnvironmentVarNames.EVENT_HUB_CONNECTION_STRING,
            streamTopic == null || streamTopic.isEmpty() ? "events-stream" : streamTopic, "many"));
    logger.info("Azure Consumer Functions v4 registration code generated");
    return List.of(consumer);
  }

  private FunctionDefinition generateGraphQLFunction() {
    return function(BoosterAzureFunctions.GRAPHQL_FUNCTION, BoosterAzureFunctions.GRAPHQL_ENTRY_POINT,
        Binding.httpTrigger(REQUEST_BINDING, List.of("POST"), "anonymous", null), Binding.httpReturn());
  }

  private FunctionDefinition generateHealthFunction() {
    return function(BoosterAzureFunctions.SENSOR_HEALTH_FUNCTION, BoosterAzureFunctions.SENSOR_HEALTH_ENTRY_POINT,
        Binding.httpTrigger(REQUEST_BINDING, List.of("GET"), "anonymous", "sensor/health/{*componentPath}"),
        Binding.httpReturn());
  }

  private FunctionDefinition generateEventHandlerFunction() {
    return function(BoosterAzureFunctions.EVENT_HANDLER_FUNCTION, BoosterAzureFunctions.EVENT_HANDLER_ENTRY_POINT,
        eventsStoreTrigger());
  }

  private FunctionDefinition generateEventStreamProducerFunction() {
    return function(BoosterAzureFunctions.EVENT_PRODUCER_FUNCTION, BoosterAzureFunctions.EVENT_PRODUCER_ENTRY_POINT,
        eventsStoreTrigger());
  }

  private Binding eventsStoreTrigger() {
    ResourceNames names = config.resourceNames();
    return Binding.cosmosDBTrigger(DOCUMENTS_BINDING, EnvironmentVarNames.COSMOS_DB_CONNECTION_STRING,
        names.applicationStack(), names.eventsStore(), LEASE_CONTAINER);
  }

  private FunctionDefinition webPubSubFunction(String name, String eventType, String eventName, String entryPoint) {
    return function(name, entryPoint, Binding.webPubSubTrigger(REQUEST_BINDING, Constants.WEB_PUB_SUB_HUB, eventType,
        eventName, OtherEnvironmentVarNames.WEB_PUB_SUB_CONNECTION_STRING));
  }

  private List<FunctionDefinition> generateSubscriptionsNotifierFunctions() {
    ResourceNames names = config.resourceNames();
    List<FunctionDefinition> functions = new ArrayList<>();
    for (String readModelName : config.readModels.keySet()) {
      functions.add(function(readModelName + BoosterAzureFunctions.SUBSCRIPTIONS_NOTIFIER_SUFFIX,
          BoosterAzureFunctions.SUBSCRIPTIONS_NOTIFIER_ENTRY_POINT,
          Binding.cosmosDBTrigger(DOCUMENTS_BINDING, EnvironmentVarNames.COSMOS_DB_CONNECTION_STRING,
              names.applicationStack(), names.forReadModel(readModelName), LEASE_CONTAINER)));
    }
    return functions;
  }

  private List<FunctionDefinition> generateScheduledFunctions() {
    List<FunctionDefinition> functions = new ArrayList<>();
    if (config.scheduledCommandHandlers == null || config.scheduledCommandHandlers.isEmpty()) {
      return functions;
    }
    for (Map.Entry<String, ScheduledCommandMetadata> entry : config.scheduledCommandHandlers.entrySet()) {
      ScheduleInterface scheduledOn = entry.getValue() == null ? null : entry.getValue().scheduledOn();
      if (scheduledOn == null || scheduledOn.isEmpty()) {
        continue;
      }
      functions.add(function(BoosterAzureFunctions.SCHEDULED_FUNCTION_PREFIX + entry.getKey(),
          BoosterAzureFunctions.SCHEDULED_COMMAND_ENTRY_POINT,
          Binding.timerTrigger(TIMER_BINDING, ScheduledFunctions.createCronExpression(scheduledOn))));
    }
    return functions;
  }

  private FunctionDefinition function(String name, String entryPoint, Binding... bindings) {
    return new FunctionDefinition(name,
        new FunctionConfig(List.of(bindings), FunctionAppLayout.scriptFile(config), entryPoint));
  }
}
