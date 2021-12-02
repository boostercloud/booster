import { TerraformStack } from 'cdktf'
import { CosmosdbAccount, ResourceGroup } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'

export class TerraformCosmosdbDatabase {
  static build(
    terraformStack: TerraformStack,
    resourceGroup: ResourceGroup,
    appPrefix: string,
    resourceGroupName: string
  ): CosmosdbAccount {
    const idAccount = toTerraformName(appPrefix, 'dba')
    return new CosmosdbAccount(terraformStack, idAccount, {
      name: `${resourceGroupName}cdba`,
      location: resourceGroup.location,
      resourceGroupName: resourceGroup.name,
      offerType: 'Standard',
      kind: 'GlobalDocumentDB',
      enableMultipleWriteLocations: false,
      isVirtualNetworkFilterEnabled: false,
      enableAutomaticFailover: true,
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
    })
  }
}
