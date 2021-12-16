import { BoosterConfig } from '@boostercloud/framework-types'
import {
  buildAppPrefix,
  readProjectConfig,
  createFunctionResourceGroupName,
  createResourceGroupName,
  createApiManagementName,
} from '../helper/utils'
import { AzurermProvider } from '@cdktf/provider-azurerm'
import { TerraformStack } from 'cdktf'
import { TerraformApplicationServicePlan } from './terraform-application-service-plan'
import { TerraformResourceGroup } from './terraform-resource-group'
import { TerraformStorageAccount } from './terraform-storage-account'
import { TerraformFunctionApp } from './terraform-function-app'
import { TerraformCosmosdbSqlDatabase } from './terraform-cosmosdb-sql-database'
import { TerraformContainers } from './terraform-containers'
import { TerraformCosmosdbDatabase } from './terraform-cosmosdb-database'
import { TerraformApiManagement } from './terraform-api-management'
import { TerraformApiManagementApi } from './terraform-api-management-api'
import { TerraformApiManagementApiOperation } from './terraform-api-management-api-operation'
import { TerraformApiManagementApiOperationPolicy } from './terraform-api-management-api-operation-policy'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class ApplicationSynth {
  readonly config: BoosterConfig
  readonly appPrefix: string
  readonly terraformStack: TerraformStack

  public constructor(terraformStack: TerraformStack) {
    this.config = readProjectConfig(process.cwd())
    this.appPrefix = buildAppPrefix(this.config)
    this.terraformStack = terraformStack
  }

  public synth(): ApplicationSynthStack {
    const resourceGroupName = createResourceGroupName(this.config.appName, this.config.environmentName)
    const functionAppName = createFunctionResourceGroupName(resourceGroupName)
    const apiManagementName = createApiManagementName(resourceGroupName)
    new AzurermProvider(this.terraformStack, 'azureFeature', {
      features: {},
    })
    const resourceGroup = TerraformResourceGroup.build(this.terraformStack, this.appPrefix, resourceGroupName)
    const applicationServicePlan = TerraformApplicationServicePlan.build(
      this.terraformStack,
      resourceGroup,
      this.appPrefix,
      resourceGroupName
    )
    const storageAccount = TerraformStorageAccount.build(
      this.terraformStack,
      resourceGroup,
      this.appPrefix,
      resourceGroupName
    )
    const cosmosdbDatabase = TerraformCosmosdbDatabase.build(
      this.terraformStack,
      resourceGroup,
      this.appPrefix,
      resourceGroupName
    )
    const cosmosdbSqlDatabase = TerraformCosmosdbSqlDatabase.build(
      this.terraformStack,
      resourceGroup,
      this.appPrefix,
      cosmosdbDatabase,
      this.config
    )
    const containers = TerraformContainers.build(
      this.terraformStack,
      resourceGroup,
      this.appPrefix,
      cosmosdbDatabase,
      cosmosdbSqlDatabase,
      this.config
    )
    const functionApp = TerraformFunctionApp.build(
      this.terraformStack,
      resourceGroup,
      applicationServicePlan,
      storageAccount,
      this.appPrefix,
      functionAppName,
      cosmosdbDatabase.name,
      apiManagementName,
      cosmosdbDatabase.primaryKey,
      this.config
    )
    const apiManagement = TerraformApiManagement.build(
      this.terraformStack,
      resourceGroup,
      apiManagementName,
      this.appPrefix
    )
    const apiManagementApi = TerraformApiManagementApi.build(
      this.terraformStack,
      resourceGroup,
      apiManagement,
      this.appPrefix,
      this.config.environmentName,
      functionApp,
      resourceGroupName
    )
    const apiManagementApiOperation = TerraformApiManagementApiOperation.build(
      this.terraformStack,
      resourceGroup,
      apiManagementApi,
      this.appPrefix
    )
    const apiManagementApiOperationPolicy = TerraformApiManagementApiOperationPolicy.build(
      this.terraformStack,
      resourceGroup,
      apiManagementApiOperation,
      this.appPrefix,
      this.config.environmentName,
      functionApp
    )

    return {
      appPrefix: this.appPrefix,
      terraformStack: this.terraformStack,
      resourceGroupName: resourceGroupName,
      functionAppName: functionAppName,
      resourceGroup: resourceGroup,
      applicationServicePlan: applicationServicePlan,
      storageAccount: storageAccount,
      functionApp: functionApp,
      apiManagement: apiManagement,
      apiManagementApi: apiManagementApi,
      apiManagementApiOperation: apiManagementApiOperation,
      apiManagementApiOperationPolicy: apiManagementApiOperationPolicy,
      cosmosdbDatabase: cosmosdbDatabase,
      cosmosdbSqlDatabase: cosmosdbSqlDatabase,
      containers: containers,
    } as ApplicationSynthStack
  }
}
