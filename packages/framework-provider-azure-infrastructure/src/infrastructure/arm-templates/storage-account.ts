export const template = {
  $schema: 'https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#',
  contentVersion: '1.0.0.0',
  parameters: {
    storageAccountName: {
      type: 'string',
      defaultValue: "[concat('st', uniqueString(resourceGroup().id))]",
    },
    location: {
      type: 'string',
      defaultValue: '[resourceGroup().location]',
    },
  },
  resources: [
    {
      type: 'Microsoft.Storage/storageAccounts',
      apiVersion: '2019-04-01',
      name: "[parameters('storageAccountName')]",
      location: "[parameters('location')]",
      sku: {
        name: 'Standard_LRS',
      },
      kind: 'StorageV2',
      properties: {},
    },
  ],
  outputs: {
    storageAccountName: {
      type: 'string',
      value: "[parameters('storageAccountName')]",
    },
  },
}
