import { TerraformStack } from 'cdktf'
import { ResourceGroup, StorageAccount } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'

export class TerraformStorageAccount {
  static build(
    terraformStack: TerraformStack,
    resourceGroup: ResourceGroup,
    appPrefix: string,
    resourceGroupName: string
  ): StorageAccount {
    const id = toTerraformName(appPrefix, 'st')
    return new StorageAccount(terraformStack, id, {
      name: `${resourceGroupName}sa`,
      resourceGroupName: resourceGroup.name,
      location: resourceGroup.location,
      accountReplicationType: 'LRS',
      accountTier: 'Standard',
    })
  }
}
