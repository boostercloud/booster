import { storageAccount } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class TerraformStorageAccount {
  static build(
    { terraformStack, azureProvider, appPrefix, resourceGroupName, resourceGroup }: ApplicationSynthStack,
    suffixName: string
  ): storageAccount.StorageAccount {
    const id = toTerraformName(appPrefix, suffixName)
    return new storageAccount.StorageAccount(terraformStack, id, {
      name: `${resourceGroupName}${suffixName}`,
      resourceGroupName: resourceGroupName,
      location: resourceGroup.location,
      accountReplicationType: 'LRS',
      accountTier: 'Standard',
      provider: azureProvider,
    })
  }
}
