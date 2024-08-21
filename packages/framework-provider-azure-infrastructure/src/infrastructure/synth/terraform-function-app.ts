import { servicePlan, storageAccount, windowsFunctionApp } from '@cdktf/provider-azurerm'
import { toTerraformName } from '../helper/utils'
import { ApplicationSynthStack } from '../types/application-synth-stack'
import { WindowsFunctionAppConfig } from '@cdktf/provider-azurerm/lib/windows-function-app'

export class TerraformFunctionApp {
  static build(
    {
      terraformStack,
      azureProvider,
      appPrefix,
      resourceGroup,
      resourceGroupName,
      cosmosdbDatabase,
    }: ApplicationSynthStack,
    applicationServicePlan: servicePlan.ServicePlan,
    storageAccount: storageAccount.StorageAccount,
    suffixName: string,
    functionAppName: string,
    zipFile?: string,
    appSettings?: { [key: string]: string }
  ): windowsFunctionApp.WindowsFunctionApp {
    if (!cosmosdbDatabase) {
      throw new Error('Undefined cosmosdbDatabase resource')
    }
    if (!applicationServicePlan) {
      throw new Error('Undefined applicationServicePlan resource')
    }
    const id = toTerraformName(appPrefix, suffixName)
    const functionConfig: Exclude<WindowsFunctionAppConfig, 'zipDeployFile'> = {
      name: functionAppName,
      location: resourceGroup.location,
      resourceGroupName: resourceGroupName,
      servicePlanId: applicationServicePlan.id,
      appSettings: appSettings ?? {},
      storageAccountName: storageAccount.name,
      storageAccountAccessKey: storageAccount.primaryAccessKey,
      dependsOn: [resourceGroup],
      lifecycle: {
        ignoreChanges: ['app_settings["WEBSITE_RUN_FROM_PACKAGE"]'],
      },
      provider: azureProvider,
      siteConfig: {
        applicationStack: {
          nodeVersion: '~18',
        },
      },
      functionsExtensionVersion: '~4',
    }
    if (zipFile) {
      return new windowsFunctionApp.WindowsFunctionApp(terraformStack, id, {
        ...functionConfig,
        zipDeployFile: zipFile,
      })
    }
    return new windowsFunctionApp.WindowsFunctionApp(terraformStack, id, functionConfig)
  }
}
