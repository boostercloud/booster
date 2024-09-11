import { cosmosdbAccount } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformCosmosdbDatabase {
  static build({
    terraformStack,
    azureProvider,
    appPrefix,
    resourceGroup,
    resourceGroupName,
  }: ApplicationSynthStack): cosmosdbAccount.CosmosdbAccount {
    const idAccount = toTerraformName(appPrefix, 'dba')
    return new cosmosdbAccount.CosmosdbAccount(terraformStack, idAccount, {
      name: `${resourceGroupName}cdba`,
      location: resourceGroup.location,
      resourceGroupName: resourceGroupName,
      offerType: 'Standard',
      kind: 'GlobalDocumentDB',
      multipleWriteLocationsEnabled: false,
      isVirtualNetworkFilterEnabled: false,
      automaticFailoverEnabled: true,
      geoLocation: [
        {
          location: resourceGroup.location,
          failoverPriority: 0,
        },
      ],
      consistencyPolicy: {
        consistencyLevel: 'Session',
      },
      dependsOn: [resourceGroup],
      provider: azureProvider,
    })
  }
}
