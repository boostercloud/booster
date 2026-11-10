package com.boostercloud.framework.provider.azure.infrastructure.functions;

import com.boostercloud.framework.provider.azure.Constants.EnvironmentVarNames;
import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;

/**
 * Port of {@code functions/event-stream-producer-function.ts}: the legacy definition of the change feed function that
 * publishes to Event Hubs (unused).
 */
public final class EventStreamProducerFunction {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public EventStreamProducerFunction(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return {@code eventProducer}: the events store {@code cosmosDBTrigger} plus an {@code eventHub} output
   *     {@code eventHubMessages}, {@code boosterProduceEventStream}
   */
  public FunctionDefinition getFunctionDefinition() {
    Binding eventHubBinding = Binding.of(LegacyFunctions.ordered(
        "name", "eventHubMessages",
        "direction", "out",
        "type", "eventHub",
        "connection", EnvironmentVarNames.EVENT_HUB_CONNECTION_STRING,
        "eventHubName", config.resourceNames().streamTopic()));
    return new FunctionDefinition("eventProducer", new FunctionConfig(
        List.of(EventHandlerFunction.cosmosBinding(config), eventHubBinding),
        config.functionRelativePath,
        LegacyFunctions.entryPoint(config.eventStreamProducer)));
  }
}
