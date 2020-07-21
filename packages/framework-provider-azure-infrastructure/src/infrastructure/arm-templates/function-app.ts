export const template = {
  $schema: 'https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#',
  contentVersion: '1.0.0.0',
  parameters: {
    storageAccountName: {
      type: 'string',
      metadata: {
        description: 'Name of the required Storage Account.',
      },
    },
    location: {
      type: 'string',
      defaultValue: '[resourceGroup().location]',
      metadata: {
        description: 'Location for all resources.',
      },
    },
    functionAppName: {
      type: 'string',
      defaultValue: '[uniqueString(resourceGroup().id)]',
      metadata: {
        description: 'Name of the Function App.',
      },
    },
  },
  functions: [],
  variables: {
    hostingPlanName: "[concat('hpn-', resourceGroup().name)]",
  },
  resources: [
    {
      name: "[parameters('functionAppName')]",
      type: 'Microsoft.Web/sites',
      kind: 'functionapp',
      apiVersion: '2018-11-01',
      location: '[resourceGroup().location]',
      tags: {
        "[concat('hidden-related:', resourceGroup().id, variables('hostingPlanName'))]": 'Resource',
        displayName: "[parameters('functionAppName')]",
      },
      dependsOn: ["[resourceId('Microsoft.Web/serverfarms', variables('hostingPlanName'))]"],
      properties: {
        name: "[parameters('functionAppName')]",
        serverFarmId: "[resourceId('Microsoft.Web/serverfarms', variables('hostingPlanName'))]",
        siteConfig: {
          appSettings: [
            {
              name: 'AzureWebJobsStorage',
              value:
                "[concat('DefaultEndpointsProtocol=https;AccountName=', parameters('storageAccountName'), ';EndpointSuffix=', environment().suffixes.storage, ';AccountKey=',listKeys(resourceId('Microsoft.Storage/storageAccounts', parameters('storageAccountName')), '2019-06-01').keys[0].value)]",
            },
            {
              name: 'WEBSITE_CONTENTAZUREFILECONNECTIONSTRING',
              value:
                "[concat('DefaultEndpointsProtocol=https;AccountName=', parameters('storageAccountName'), ';EndpointSuffix=', environment().suffixes.storage, ';AccountKey=',listKeys(resourceId('Microsoft.Storage/storageAccounts', parameters('storageAccountName')), '2019-06-01').keys[0].value)]",
            },
            {
              name: 'WEBSITE_CONTENTSHARE',
              value: "[toLower(parameters('functionAppName'))]",
            },
            {
              name: 'FUNCTIONS_EXTENSION_VERSION',
              value: '~3',
            },
            {
              name: 'WEBSITE_NODE_DEFAULT_VERSION',
              value: '~10',
            },
            {
              name: 'FUNCTIONS_WORKER_RUNTIME',
              value: 'node',
            },
          ],
        },
        hostingEnvironment: '',
        clientAffinityEnabled: true,
      },
    },
    {
      type: 'Microsoft.Web/serverfarms',
      apiVersion: '2019-08-01',
      name: "[variables('hostingPlanName')]",
      location: "[parameters('location')]",
      kind: '',
      properties: {
        name: "[variables('hostingPlanName')]",
        workerSize: '0',
        workerSizeId: '0',
        numberOfWorkers: '1',
        hostingEnvironment: '',
      },
      sku: {
        Tier: 'Dynamic',
        Name: 'Y1',
      },
    },
  ],
  outputs: {
    functionAppName: {
      type: 'string',
      value: "[parameters('functionAppName')]",
    },
  },
}
