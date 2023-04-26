import { TerraformStack } from 'cdktf'
import { cosmosdbAccount, resourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export class TerraformCosmosdbDatabase {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    appPrefix: string,
    resourceGroupName: string
  ): cosmosdbAccount.CosmosdbAccount {
    const idAccount = toTerraformName(appPrefix, 'dba')
    return new cosmosdbAccount.CosmosdbAccount(terraformStackResource, idAccount, {
      name: `${resourceGroupName}cdba`,
      location: resourceGroupResource.location,
      resourceGroupName: resourceGroupResource.name,
      offerType: 'Standard',
      kind: 'GlobalDocumentDB',
      enableMultipleWriteLocations: false,
      isVirtualNetworkFilterEnabled: false,
      enableAutomaticFailover: true,
      geoLocation: [
        {
          location: resourceGroupResource.location,
          failoverPriority: 0,
        },
      ],
      consistencyPolicy: {
        consistencyLevel: 'Session',
      },
      dependsOn: [resourceGroupResource],
      provider: providerResource,
    })
  }
}
