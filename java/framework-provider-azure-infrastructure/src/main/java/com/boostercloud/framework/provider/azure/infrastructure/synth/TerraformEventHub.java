package com.boostercloud.framework.provider.azure.infrastructure.synth;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformResource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.EventStreamConfiguration;

/** Port of {@code synth/terraform-event-hub.ts}: the {@code azurerm_eventhub}. */
public final class TerraformEventHub {

  /** {@code DEFAULT_PARTITION_COUNT}, used when the stream has no parameters. */
  public static final int DEFAULT_PARTITION_COUNT = 1;

  /** {@code DEFAULT_MESSAGE_RETENTION} (days), used when the stream has no parameters. */
  public static final int DEFAULT_MESSAGE_RETENTION = 1;

  private TerraformEventHub() {}

  /**
   * Construct id {@code toTerraformName(appPrefix, 'eh')}, name = the stream topic, partitions and retention from
   * {@code eventStreamConfiguration.parameters}.
   *
   * @param stack the stack bag
   * @param config the configuration
   * @return the resource
   * @throws IllegalStateException {@code Undefined eventHubNamespace resource}
   */
  public static TerraformResource build(ApplicationSynthStack stack, BoosterConfig config) {
    if (stack.eventHubNamespace == null) {
      throw new IllegalStateException("Undefined eventHubNamespace resource");
    }
    EventStreamConfiguration.Parameters parameters = config.eventStreamConfiguration.parameters();
    String id = Utils.toTerraformName(stack.appPrefix, "eh");
    return new TerraformResource(stack.terraformStack, id, "azurerm_eventhub")
        .setAttributes(Attributes.of(
            "name", stack.eventHubName,
            "namespace_id", stack.eventHubNamespace.ref("id"),
            // Cannot be changed unless Eventhub Namespace SKU is Premium
            "partition_count", parameters != null ? parameters.partitionCount() : DEFAULT_PARTITION_COUNT,
            "message_retention", parameters != null ? parameters.messageRetention() : DEFAULT_MESSAGE_RETENTION))
        .provider(stack.azureProvider);
  }
}
