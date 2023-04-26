import { TerraformStack } from 'cdktf'
import { resourceGroup, storageAccount } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { AzurermProvider } from '@cdktf/provider-azurerm/lib/provider'

export class TerraformStorageAccount {
  static build(
    providerResource: AzurermProvider,
    terraformStackResource: TerraformStack,
    resourceGroupResource: resourceGroup.ResourceGroup,
    appPrefix: string,
    resourceGroupName: string
  ): storageAccount.StorageAccount {
    const id = toTerraformName(appPrefix, 'st')
    return new storageAccount.StorageAccount(terraformStackResource, id, {
      name: `${resourceGroupName}sa`,
      resourceGroupName: resourceGroupResource.name,
      location: resourceGroupResource.location,
      accountReplicationType: 'LRS',
      accountTier: 'Standard',
      provider: providerResource,
    })
  }
}
