import { BoosterConfig } from '@boostercloud/framework-types'
import { buildAppPrefix, readProjectConfig } from '../helper/utils'
import { AzurermProvider } from '@cdktf/provider-azurerm'
import { TerraformStack } from 'cdktf'
import { TerraformApplicationServicePlan } from './TerraformApplicationServicePlan'
import { TerraformResourceGroup } from './TerraformResourceGroup'
import { TerraformStorageAccount } from './TerraformStorageAccount'
import { createFunctionResourceGroupName, createResourceGroupName } from '../helper/setup'
import { TerraformFunctionApp } from './TerraformFunctionApp'
import { TerraformCosmosdbSqlDatabase } from './TerraformCosmosdbSqlDatabase'
import { TerraformContainers } from './TerraformContainers'
import { TerraformCosmosdbDatabase } from './TerraformCosmosdbDatabase'
import { TerraformApiManagement } from './TerraformApiManagement'

export class ApplicationSynth {
  readonly config: BoosterConfig
  readonly appPrefix: string

  public constructor() {
    this.config = readProjectConfig(process.cwd())
    this.appPrefix = buildAppPrefix(this.config)
  }

  public async synth(terraformStack: TerraformStack): Promise<void> {
    const resourceGroupName = createResourceGroupName(this.config)
    const functionAppName = createFunctionResourceGroupName(resourceGroupName)
    new AzurermProvider(terraformStack, 'azureFeature', {
      features: {},
    })
    const resourceGroup = TerraformResourceGroup.build(terraformStack, this.appPrefix, resourceGroupName)
    const applicationServicePlan = TerraformApplicationServicePlan.build(
      terraformStack,
      resourceGroup,
      this.appPrefix,
      resourceGroupName
    )
    const storageAccount = TerraformStorageAccount.build(
      terraformStack,
      resourceGroup,
      this.appPrefix,
      resourceGroupName
    )
    const functionApp = TerraformFunctionApp.build(
      terraformStack,
      resourceGroup,
      applicationServicePlan,
      storageAccount,
      this.appPrefix,
      functionAppName
    )
    const apiManagement = TerraformApiManagement.build(
      terraformStack,
      resourceGroup,
      this.appPrefix,
      this.config.environmentName,
      functionApp,
      resourceGroupName
    )
    const cosmosdbDatabase = TerraformCosmosdbDatabase.build(
      terraformStack,
      resourceGroup,
      this.appPrefix,
      resourceGroupName
    )
    const cosmosdbSqlDatabase = TerraformCosmosdbSqlDatabase.build(
      terraformStack,
      resourceGroup,
      this.appPrefix,
      cosmosdbDatabase,
      this.config
    )
    TerraformContainers.build(
      terraformStack,
      resourceGroup,
      this.appPrefix,
      cosmosdbDatabase,
      cosmosdbSqlDatabase,
      this.config
    )
    TerraformFunctionApp.updateFunction(
      functionApp,
      cosmosdbDatabase.name,
      apiManagement.name,
      cosmosdbDatabase.primaryKey,
      this.config
    )

    // TODO call Rocket.terraform({provider: 'azure', info:{storageAccount, containers, etc...}} as AzureTerraformRocketInformation extends TerraformRocketInformation)
  }
}
