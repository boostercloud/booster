import { Construct } from 'constructs'
import { App, TerraformStack } from 'cdktf'
import {
  ApplicationInsights,
  AppServicePlan,
  AzurermProvider,
  FunctionApp,
  ResourceGroup,
  StorageAccount,
} from '@cdktf/provider-azurerm'
import { UUID } from 'framework-types/dist'

class AzureStack extends TerraformStack {
  constructor(scope: Construct, name: string) {
    super(scope, name)

    const appPrefix = 'booster'

    new AzurermProvider(this, 'azureFeature', {
      features: {},
    })

    const rg = new ResourceGroup(this, `${appPrefix}-rg`, {
      name: 'demo2020',
      location: 'westeurope',
    })

    const insights = new ApplicationInsights(this, `${appPrefix}-insights`, {
      name: 'func-application-insights',
      location: rg.location,
      resourceGroupName: rg.name,
      applicationType: 'Node.JS',
      dependsOn: [rg],
    })

    const asp = new AppServicePlan(this, `${appPrefix}-asp`, {
      name: '${appPrefix}-func',
      location: rg.location,
      resourceGroupName: rg.name,
      kind: 'FunctionApp',
      reserved: true,
      sku: { size: 'TY', tier: 'Dynamic' },
      dependsOn: [rg],
    })

    const storageAccount = new StorageAccount(this, `${appPrefix}-storage`, {
      name: `${appPrefix}-sa-${UUID.generate()}`,
      resourceGroupName: rg.name,
      location: rg.location,
      accountReplicationType: 'LRS',
      accountTier: 'Standard',
    })

    new FunctionApp(this, `${appPrefix}-func`, {
      name: '${appPrefix}-func',
      location: rg.location,
      resourceGroupName: rg.name,
      appServicePlanId: asp.id,
      appSettings: {
        FUNCTIONS_WORKER_RUNTIME: 'node',
        // TODO: Figure out this
        AzureWebJobsStorage: storageAccount.primaryConnectionString,
        APPINSIGHTS_INSTRUMENTATIONKEY: insights.instrumentationKey,
        WEBSITE_RUN_FROM_PACKAGE: '',
      },

      osType: 'linux',
      storageAccountName: storageAccount.name,
      storageAccountAccessKey: storageAccount.primaryAccessKey,
      version: '~3',

      lifecycle: {
        ignoreChanges: ["app_settings['WEBSITE_RUN_FROM_PACKAGE']"],
      },
    })
  }
}

const app = new App()
new AzureStack(app, 'azure-app-service-docker')

app.synth()
