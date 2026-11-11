package com.boostercloud.framework.provider.azure.infrastructure.functions;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.azure.infrastructure.support.Fixtures;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The legacy definitions TypeScript does not test. */
class LegacyFunctionsTest {

  private final BoosterConfig config = Fixtures.config();

  @Test
  void sensor_health() {
    FunctionDefinition definition = new SensorHealthFunction(config).getFunctionDefinition();
    assertThat(definition.name()).isEqualTo("sensor-health");
    assertThat(definition.config().bindings().get(0).properties()).isEqualTo(Map.of(
        "authLevel", "anonymous", "type", "httpTrigger", "direction", "in", "name", "rawRequest",
        "methods", List.of("get"), "route", "sensor/health/{*url}"));
    assertThat(definition.config().entryPoint()).isEqualTo("boosterHealth");
  }

  @Test
  void event_stream_producer_and_consumer() {
    FunctionDefinition producer = new EventStreamProducerFunction(config).getFunctionDefinition();
    assertThat(producer.name()).isEqualTo("eventProducer");
    assertThat(producer.config().bindings()).hasSize(2);
    assertThat(producer.config().bindings().get(1).properties()).isEqualTo(Map.of(
        "name", "eventHubMessages", "direction", "out", "type", "eventHub",
        "connection", "EVENTHUB_CONNECTION_STRING", "eventHubName", "booster_events"));
    assertThat(producer.config().entryPoint()).isEqualTo("boosterProduceEventStream");

    FunctionDefinition consumer = new EventStreamConsumerFunction(config).getFunctionDefinition();
    assertThat(consumer.name()).isEqualTo("eventHandler");
    assertThat(consumer.config().bindings().get(0).properties()).isEqualTo(Map.of(
        "type", "eventHubTrigger", "name", "eventHubMessages", "direction", "in", "eventHubName", "booster_events",
        "connection", "EVENTHUB_CONNECTION_STRING", "cardinality", "many", "consumerGroup", "$Default",
        "dataType", "string"));
    assertThat(consumer.config().entryPoint()).isEqualTo("boosterConsumeEventStream");
  }

  @Test
  void subscriptions_notifiers_have_one_lease_container_per_read_model() {
    List<FunctionDefinition> definitions = new SubscriptionsNotifierFunction(config).getFunctionDefinition();
    assertThat(definitions).extracting(FunctionDefinition::name)
        .containsExactly("CartReadModel-subscriptions-notifier", "ProductReadModel-subscriptions-notifier");
    assertThat(definitions.get(0).config().bindings().get(0).get("leaseContainerName"))
        .isEqualTo("leases-CartReadModel");
    assertThat(definitions.get(0).config().bindings().get(0).get("containerName"))
        .isEqualTo("my-store-app-CartReadModel");
    assertThat(definitions.get(0).config().bindings().get(1).properties()).isEqualTo(Map.of(
        "type", "webPubSubConnection", "name", "connection", "hub", "booster", "direction", "out"));
    assertThat(definitions.get(0).config().entryPoint()).isEqualTo("boosterNotifySubscribers");
  }

  @Test
  void websocket_functions_hard_code_the_script_and_entry_point() {
    assertWebsocket(new WebsocketConnectFunction(config).getFunctionDefinition(), "connect", "system", "connect");
    assertWebsocket(new WebsocketDisconnectFunction(config).getFunctionDefinition(), "disconnect", "system",
        "disconnect");
    assertWebsocket(new WebsocketMessagesFunction(config).getFunctionDefinition(), "messages", "user", "message");
  }

  private static void assertWebsocket(FunctionDefinition definition, String name, String eventType, String eventName) {
    assertThat(definition.name()).isEqualTo(name);
    assertThat(definition.config().bindings().get(0).properties()).isEqualTo(Map.of(
        "type", "webPubSubTrigger", "direction", "in", "name", "data", "hub", "booster",
        "eventType", eventType, "eventName", eventName));
    assertThat(definition.config().scriptFile()).isEqualTo("../dist/index.js");
    assertThat(definition.config().entryPoint()).isEqualTo("boosterServeGraphQL");
  }
}
