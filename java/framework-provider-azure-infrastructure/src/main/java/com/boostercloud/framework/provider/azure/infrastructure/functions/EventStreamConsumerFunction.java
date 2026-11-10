package com.boostercloud.framework.provider.azure.infrastructure.functions;

import com.boostercloud.framework.provider.azure.Constants.EnvironmentVarNames;
import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;

/**
 * Port of {@code functions/event-stream-consumer-function.ts}: the legacy definition of the Event Hub consumer
 * (unused). Its name is {@code eventHandler}, as in TypeScript.
 */
public final class EventStreamConsumerFunction {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public EventStreamConsumerFunction(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return {@code eventHandler}: {@code eventHubTrigger} {@code eventHubMessages} (many, {@code $Default},
   *     {@code string}), {@code boosterConsumeEventStream}
   */
  public FunctionDefinition getFunctionDefinition() {
    Binding binding = Binding.of(LegacyFunctions.ordered(
        "type", "eventHubTrigger",
        "name", "eventHubMessages",
        "direction", "in",
        "eventHubName", config.resourceNames().streamTopic(),
        "connection", EnvironmentVarNames.EVENT_HUB_CONNECTION_STRING,
        "cardinality", "many",
        "consumerGroup", "$Default",
        "dataType", "string"));
    return new FunctionDefinition("eventHandler", new FunctionConfig(
        List.of(binding),
        config.functionRelativePath,
        LegacyFunctions.entryPoint(config.eventStreamConsumer)));
  }
}
