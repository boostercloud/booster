package com.boostercloud.framework.provider.azure.infrastructure.functions;

import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code functions/subscriptions-notifier-function.ts}: the legacy per-read-model notifier definitions
 * (unused).
 */
public final class SubscriptionsNotifierFunction {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public SubscriptionsNotifierFunction(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return one {@code <ReadModel>-subscriptions-notifier} per read model: {@code cosmosDBTrigger} {@code rawEvent} on
   *     the read model container with the lease container {@code leases-<ReadModel>} and a
   *     {@code webPubSubConnection} output, {@code boosterNotifySubscribers}
   */
  public List<FunctionDefinition> getFunctionDefinition() {
    List<FunctionDefinition> definitions = new ArrayList<>();
    for (String readModel : config.readModels.keySet()) {
      definitions.add(new FunctionDefinition(readModel + "-subscriptions-notifier", new FunctionConfig(
          List.of(
              Binding.of(LegacyFunctions.ordered(
                  "type", "cosmosDBTrigger",
                  "name", "rawEvent",
                  "direction", "in",
                  "leaseContainerName", "leases-" + readModel,
                  "connection", "COSMOSDB_CONNECTION_STRING",
                  "databaseName", config.resourceNames().applicationStack(),
                  "containerName", config.resourceNames().forReadModel(readModel),
                  "createLeaseContainerIfNotExists", "true")),
              Binding.of(LegacyFunctions.ordered(
                  "type", "webPubSubConnection", "name", "connection", "hub", "booster", "direction", "out"))),
          config.functionRelativePath,
          LegacyFunctions.entryPoint(config.notifySubscribersHandler))));
    }
    return definitions;
  }
}
