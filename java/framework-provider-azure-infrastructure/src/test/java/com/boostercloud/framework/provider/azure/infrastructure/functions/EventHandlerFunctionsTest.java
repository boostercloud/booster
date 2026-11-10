package com.boostercloud.framework.provider.azure.infrastructure.functions;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import org.junit.jupiter.api.Test;

/** Port of {@code test/infrastructure/functions/event-handler-functions.test.ts}. */
class EventHandlerFunctionsTest {

  // TypeScript assigns resourceNames.applicationStack/eventsStore, which has no effect (resourceNames is a getter)
  private final BoosterConfig config = new BoosterConfig("test");

  @Test
  void create_the_expected_EventHandlerFunctionDefinition() {
    FunctionDefinition definition = new EventHandlerFunction(config).getFunctionDefinition();
    assertThat(definition).isNotNull();
    assertThat(definition.name()).isEqualTo("eventHandler");
    Binding binding = definition.config().bindings().get(0);
    assertThat(binding.type()).isEqualTo("cosmosDBTrigger");
    assertThat(binding.name()).isEqualTo("rawEvent");
    assertThat(binding.direction()).isEqualTo("in");
    assertThat(binding.get("leaseContainerName")).isEqualTo("leases");
    assertThat(binding.get("connection")).isEqualTo("COSMOSDB_CONNECTION_STRING");
    assertThat(binding.get("databaseName")).isEqualTo("new-booster-app-app");
    assertThat(binding.get("containerName")).isEqualTo("new-booster-app-app-events-store");
    assertThat(binding.get("createLeaseContainerIfNotExists")).isEqualTo("true");
    assertThat(definition.config().scriptFile()).isEqualTo("../dist/index.js");
    assertThat(definition.config().entryPoint()).isEqualTo("boosterEventDispatcher");
  }
}
