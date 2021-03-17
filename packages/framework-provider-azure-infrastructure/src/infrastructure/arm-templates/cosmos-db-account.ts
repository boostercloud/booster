export const template = {
  $schema: 'https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#',
  contentVersion: '1.0.0.0',
  parameters: {
    cosmosDbAccountName: {
      type: 'string',
      defaultValue: '[uniqueString(resourceGroup().id)]',
    },
    defaultExperience: {
      type: 'string',
      defaultValue: 'Core (SQL)',
    },
    databaseName: {
      type: 'string',
    },
  },
  functions: [],
  variables: {
    resourceId: "[resourceId('Microsoft.DocumentDB/databaseAccounts', parameters('cosmosDbAccountName'))]",
    apiVersion: "[providers('Microsoft.DocumentDB', 'databaseAccounts').apiVersions[0]]",
  },
  resources: [
    {
      name: "[parameters('cosmosDbAccountName')]",
      type: 'Microsoft.DocumentDB/databaseAccounts',
      apiVersion: '2019-12-12',
      location: '[resourceGroup().location]',
      tags: {
        defaultExperience: "[parameters('defaultExperience')]",
        'hidden-cosmos-mmspecial': '',
        CosmosAccountType: 'Non-Production',
      },
      kind: 'GlobalDocumentDB',
      properties: {
        databaseAccountOfferType: 'Standard',
        locations: [
          {
            id: "[concat(parameters('cosmosDbAccountName'), '-', resourceGroup().location)]",
            locationName: '[resourceGroup().location]',
            failoverPriority: 0,
          },
        ],
        enableMultipleWriteLocations: false,
        isVirtualNetworkFilterEnabled: false,
        virtualNetworkRules: [],
        ipRangeFilter: '',
        dependsOn: [],
        capabilities: [],
        enableFreeTier: false,
      },
      resources: [
        {
          type: 'Microsoft.DocumentDB/databaseAccounts/apis/databases',
          name: "[concat(parameters('cosmosDbAccountName'), '/sql/', parameters('databaseName'))]",
          apiVersion: '2016-03-31',
          dependsOn: ["[resourceId('Microsoft.DocumentDB/databaseAccounts/', parameters('cosmosDbAccountName'))]"],
          properties: {
            resource: {
              id: "[parameters('databaseName')]",
            },
            options: {
              throughput: 400,
            },
          },
        },
      ],
    },
  ],
  outputs: {
    documentEndpoint: {
      type: 'string',
      value: "[reference(variables('resourceId'), variables('apiVersion')).documentEndpoint]",
    },
    accountKey: {
      type: 'string',
      value: "[listKeys(variables('resourceId'), variables('apiVersion')).primaryMasterKey]",
    },
    connectionString: {
      type: 'string',
      value:
        "[concat('AccountEndpoint=https://', parameters('cosmosDbAccountName'), '.documents.azure.com:443/;AccountKey=', listKeys(variables('resourceId'), variables('apiVersion')).primaryMasterKey, ';')]",
    },
  },
}
