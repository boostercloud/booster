package com.boostercloud.framework.provider.azure.infrastructure.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions;
import com.boostercloud.framework.provider.azure.infrastructure.support.Fixtures;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.EventStreamConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The function definitions of the Java function apps (the counterpart of the generated {@code functions.js}):
 * names, triggers and entry points for the four feature combinations.
 */
class FunctionsCodeGeneratorTest {

  private static final String JAR = "../my-store.jar";

  private static List<String> names(List<FunctionDefinition> definitions) {
    return definitions.stream().map(FunctionDefinition::name).toList();
  }

  @Test
  void main_app_with_subscriptions_and_without_streaming() {
    List<FunctionDefinition> functions = new FunctionsCodeGenerator(Fixtures.config()).generateFunctionsCode();
    assertThat(names(functions)).containsExactly("graphql", "sensor-health", "eventHandler", "connect", "disconnect",
        "messages", "CartReadModel-subscriptions-notifier", "ProductReadModel-subscriptions-notifier",
        "scheduleFunction-CheckCart");
    assertThat(functions).allSatisfy(function -> assertThat(function.config().scriptFile()).isEqualTo(JAR));
    assertThat(functions).extracting(function -> function.config().entryPoint()).containsExactly(
        BoosterAzureFunctions.GRAPHQL_ENTRY_POINT,
        BoosterAzureFunctions.SENSOR_HEALTH_ENTRY_POINT,
        BoosterAzureFunctions.EVENT_HANDLER_ENTRY_POINT,
        BoosterAzureFunctions.CONNECT_ENTRY_POINT,
        BoosterAzureFunctions.DISCONNECT_ENTRY_POINT,
        BoosterAzureFunctions.MESSAGES_ENTRY_POINT,
        BoosterAzureFunctions.SUBSCRIPTIONS_NOTIFIER_ENTRY_POINT,
        BoosterAzureFunctions.SUBSCRIPTIONS_NOTIFIER_ENTRY_POINT,
        BoosterAzureFunctions.SCHEDULED_COMMAND_ENTRY_POINT);
  }

  @Test
  void main_app_with_streaming_and_without_subscriptions() {
    BoosterConfig config = Fixtures.streamingConfig();
    config.enableSubscriptions = false;
    List<FunctionDefinition> functions = new FunctionsCodeGenerator(config).generateFunctionsCode();
    assertThat(names(functions)).containsExactly("graphql", "sensor-health", "eventProducer",
        "scheduleFunction-CheckCart");
    assertThat(functions.get(2).config().entryPoint()).isEqualTo(BoosterAzureFunctions.EVENT_PRODUCER_ENTRY_POINT);
  }

  @Test
  void main_app_without_scheduled_commands() {
    BoosterConfig config = Fixtures.noSubscriptionsConfig();
    config.scheduledCommandHandlers.clear();
    assertThat(names(new FunctionsCodeGenerator(config).generateFunctionsCode()))
        .containsExactly("graphql", "sensor-health", "eventHandler");
  }

  @Test
  void writes_the_graphql_and_health_http_triggers() {
    List<FunctionDefinition> functions = new FunctionsCodeGenerator(Fixtures.config()).generateFunctionsCode();
    assertThat(FunctionZip.functionJson(functions.get(0).config())).isEqualTo("""
        {
          "bindings": [
            {
              "type": "httpTrigger",
              "direction": "in",
              "name": "request",
              "methods": [
                "POST"
              ],
              "authLevel": "anonymous"
            },
            {
              "type": "http",
              "direction": "out",
              "name": "$return"
            }
          ],
          "scriptFile": "../my-store.jar",
          "entryPoint": "com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions.graphql"
        }""");
    assertThat(functions.get(1).config().bindings().get(0).properties()).isEqualTo(Map.of(
        "type", "httpTrigger", "direction", "in", "name", "request", "methods", List.of("GET"),
        "authLevel", "anonymous", "route", "sensor/health/{*componentPath}"));
  }

  @Test
  void writes_the_cosmos_db_triggers_with_the_shared_lease_container() {
    List<FunctionDefinition> functions = new FunctionsCodeGenerator(Fixtures.config()).generateFunctionsCode();
    assertThat(FunctionZip.functionJson(functions.get(2).config())).isEqualTo("""
        {
          "bindings": [
            {
              "type": "cosmosDBTrigger",
              "direction": "in",
              "name": "documents",
              "connection": "COSMOSDB_CONNECTION_STRING",
              "databaseName": "my-store-app",
              "containerName": "my-store-app-events-store",
              "createLeaseContainerIfNotExists": true,
              "leaseContainerName": "leases",
              "dataType": "string"
            }
          ],
          "scriptFile": "../my-store.jar",
          "entryPoint": "com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions.eventHandler"
        }""");
    assertThat(functions.get(6).config().bindings().get(0).get("containerName")).isEqualTo("my-store-app-CartReadModel");
    assertThat(functions.get(6).config().bindings().get(0).get("leaseContainerName")).isEqualTo("leases");
  }

  @Test
  void writes_the_web_pub_sub_triggers_without_return_binding() {
    List<FunctionDefinition> functions = new FunctionsCodeGenerator(Fixtures.config()).generateFunctionsCode();
    assertThat(functions.get(3).config().bindings()).hasSize(1);
    assertThat(functions.get(3).config().bindings().get(0).properties()).isEqualTo(Map.of(
        "type", "webPubSubTrigger", "direction", "in", "name", "request", "hub", "booster", "eventType", "system",
        "eventName", "connect", "connection", "WebPubSubConnectionString"));
    assertThat(functions.get(4).config().bindings().get(0).get("eventName")).isEqualTo("disconnect");
    assertThat(functions.get(5).config().bindings().get(0).get("eventType")).isEqualTo("user");
    assertThat(functions.get(5).config().bindings().get(0).get("eventName")).isEqualTo("message");
  }

  @Test
  void writes_the_timer_with_the_ncrontab_schedule() {
    List<FunctionDefinition> functions = new FunctionsCodeGenerator(Fixtures.config()).generateFunctionsCode();
    assertThat(functions.get(8).config().bindings().get(0).properties()).isEqualTo(Map.of(
        "type", "timerTrigger", "direction", "in", "name", "timer", "schedule", "0 0/5 * * * *"));
  }

  @Test
  void consumer_app_has_the_event_hub_trigger() {
    List<FunctionDefinition> functions =
        new FunctionsCodeGenerator(Fixtures.streamingConfig()).generateConsumerFunctionsCode();
    assertThat(names(functions)).containsExactly("eventConsumer");
    assertThat(functions.get(0).config().bindings().get(0).properties()).isEqualTo(Map.of(
        "type", "eventHubTrigger", "direction", "in", "name", "messages", "connection", "EVENTHUB_CONNECTION_STRING",
        "eventHubName", "cart-events", "cardinality", "many", "dataType", "string"));
    assertThat(functions.get(0).config().entryPoint()).isEqualTo(BoosterAzureFunctions.EVENT_CONSUMER_ENTRY_POINT);
  }

  @Test
  void consumer_app_uses_events_stream_for_an_empty_topic_and_is_empty_without_streaming() {
    BoosterConfig config = Fixtures.config();
    config.eventStreamConfiguration = EventStreamConfiguration.enabled(EventStreamConfiguration.Parameters.of("", 1, 1));
    assertThat(new FunctionsCodeGenerator(config).generateConsumerFunctionsCode().get(0).config().bindings().get(0)
        .get("eventHubName")).isEqualTo("events-stream");
    assertThat(new FunctionsCodeGenerator(Fixtures.config()).generateConsumerFunctionsCode()).isEmpty();
  }

  @Test
  void logs_the_typescript_messages() {
    BoosterConfig config = Fixtures.config();
    Logger logger = Mockito.mock(Logger.class);
    config.logger = logger;
    new FunctionsCodeGenerator(config).generateFunctionsCode();
    Mockito.verify(logger).info("[Booster]|FunctionsCodeGenerator#generateFunctionsCode: ",
        "Generating Azure Functions v4 registration code");
    Mockito.verify(logger).info("[Booster]|FunctionsCodeGenerator#generateFunctionsCode: ",
        "Azure Functions v4 registration code generated");
  }
}
