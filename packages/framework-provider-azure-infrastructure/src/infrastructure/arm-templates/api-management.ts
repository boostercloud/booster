export const template = {
  $schema: 'https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#',
  contentVersion: '1.0.0.0',
  parameters: {
    publisherEmail: {
      type: 'string',
      minLength: 1,
      metadata: {
        description: 'The email address of the owner of the service',
      },
    },
    publisherName: {
      type: 'string',
      minLength: 1,
      metadata: {
        description: 'The name of the owner of the service',
      },
    },
    sku: {
      type: 'string',
      allowedValues: ['Consumption', 'Developer', 'Standard', 'Premium'],
      defaultValue: 'Consumption',
      metadata: {
        description: 'The pricing tier of this API Management service',
      },
    },
    skuCount: {
      type: 'int',
      allowedValues: [0, 1, 2],
      defaultValue: 0,
      metadata: {
        description: 'The instance size of this API Management service.',
      },
    },
    location: {
      type: 'string',
      defaultValue: '[resourceGroup().location]',
      metadata: {
        description: 'Location for all resources.',
      },
    },
    apiName: {
      type: 'string',
    },
    apiDisplayName: {
      type: 'string',
    },
    apiPath: {
      type: 'string',
    },
    policy: {
      type: 'string',
    },
  },
  variables: {
    apiManagementServiceName: "[concat('apiservice', uniqueString(resourceGroup().id))]",
  },
  resources: [
    {
      apiVersion: '2019-12-01',
      name: "[variables('apiManagementServiceName')]",
      type: 'Microsoft.ApiManagement/service',
      location: "[parameters('location')]",
      tags: {},
      sku: {
        name: "[parameters('sku')]",
        capacity: "[parameters('skuCount')]",
      },
      properties: {
        publisherEmail: "[parameters('publisherEmail')]",
        publisherName: "[parameters('publisherName')]",
      },
      resources: [
        {
          name: "[parameters('apiName')]",
          type: 'apis',
          dependsOn: ["[concat('Microsoft.ApiManagement/service/', variables('apiManagementServiceName'))]"],
          apiVersion: '2019-12-01',
          properties: {
            path: "[parameters('apiPath')]",
            displayName: "[parameters('apiDisplayName')]",
            protocols: ['http', 'https'],
            subscriptionRequired: false,
          },
          resources: [
            {
              apiVersion: '2017-03-01',
              type: 'operations',
              name: 'graphqlPOST',
              dependsOn: [
                "[concat('Microsoft.ApiManagement/service/', variables('apiManagementServiceName'), '/apis/', parameters('apiName'))]",
              ],
              properties: {
                displayName: '/graphql',
                method: 'POST',
                urlTemplate: '/graphql',
              },
              resources: [
                {
                  apiVersion: '2017-03-01',
                  type: 'policies',
                  name: 'policy',
                  dependsOn: [
                    "[concat('Microsoft.ApiManagement/service/', variables('apiManagementServiceName'))]",
                    "[concat('Microsoft.ApiManagement/service/', variables('apiManagementServiceName'), '/apis/', parameters('apiName'))]",
                    "[concat('Microsoft.ApiManagement/service/', variables('apiManagementServiceName'), '/apis/', parameters('apiName'), '/operations/graphqlPOST')]",
                  ],
                  properties: {
                    policyContent: "[parameters('policy')]",
                  },
                },
              ],
            },
          ],
        },
      ],
    },
  ],
  outputs: {
    apiManagementServiceName: {
      type: 'string',
      value: "[variables('apiManagementServiceName')]",
    },
  },
}
