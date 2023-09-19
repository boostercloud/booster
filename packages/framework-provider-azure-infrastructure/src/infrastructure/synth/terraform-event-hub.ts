import { eventhub } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { ApplicationSynthStack } from '../types/application-synth-stack'
import { BoosterConfig } from '@boostercloud/framework-types'

export class TerraformEventHub {
  private static readonly DEFAULT_PARTITION_COUNT = 1

  private static readonly DEFAULT_MESSAGE_RETENTION = 1

  static build(
    {
      terraformStack,
      azureProvider,
      appPrefix,
      resourceGroupName,
      eventHubNamespace,
      eventHubName,
    }: ApplicationSynthStack,
    config: BoosterConfig
  ): eventhub.Eventhub {
    if (!eventHubNamespace) {
      throw new Error('Undefined eventHubNamespace resource')
    }
    const idApiManagement = toTerraformName(appPrefix, 'eh')
    return new eventhub.Eventhub(terraformStack, idApiManagement, {
      name: eventHubName,
      resourceGroupName: resourceGroupName,
      provider: azureProvider,
      namespaceName: eventHubNamespace.name,
      partitionCount: config.eventStreamConfiguration.parameters?.partitionCount ?? this.DEFAULT_PARTITION_COUNT, // Changing this will force-recreate the resource. Cannot be changed unless Eventhub Namespace SKU is Premium
      messageRetention: config.eventStreamConfiguration.parameters?.messageRetention ?? this.DEFAULT_MESSAGE_RETENTION, // Specifies the number of days to retain the events for this Event Hub.
    })
  }
}
