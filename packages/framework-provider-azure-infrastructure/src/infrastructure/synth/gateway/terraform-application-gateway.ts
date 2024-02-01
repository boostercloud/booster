import { applicationGateway } from '@cdktf/provider-azurerm'
import { ApplicationSynthStack } from '../../types/application-synth-stack'

export class TerraformApplicationGateway {
  static build({
    terraformStack,
    azureProvider,
    resourceGroup,
    resourceGroupName,
    functionAppName,
    virtualNetwork,
    subnet,
    publicIP,
  }: ApplicationSynthStack): applicationGateway.ApplicationGateway {
    const environment = process.env.BOOSTER_ENV ?? 'azure'
    if (!virtualNetwork) {
      throw new Error('Undefined virtualNetwork resource')
    }
    if (!subnet) {
      throw new Error('Undefined subnet resource')
    }
    if (!publicIP) {
      throw new Error('Undefined publicIP resource')
    }
    return new applicationGateway.ApplicationGateway(terraformStack, 'ag', {
      name: `${resourceGroupName}apigw`,
      location: resourceGroup.location,
      resourceGroupName: resourceGroupName,
      provider: azureProvider,
      sku: {
        name: 'WAF_v2',
        tier: 'WAF_v2',
        capacity: 1,
      },
      wafConfiguration: {
        enabled: true,
        firewallMode: 'Detection',
        ruleSetVersion: '3.0',
      },
      gatewayIpConfiguration: [
        {
          name: 'myGatewayIpConfiguration',
          subnetId: subnet.id,
        },
      ],
      frontendIpConfiguration: [
        {
          name: 'myFrontendIpConfiguration',
          publicIpAddressId: publicIP.id,
        },
      ],
      frontendPort: [
        {
          name: 'myFrontendPort',
          port: 80,
        },
      ],
      backendAddressPool: [
        {
          name: 'functionManagementBackendAddressPool',
          fqdns: [functionAppName + '.azurewebsites.net'],
        },
      ],
      httpListener: [
        {
          protocol: 'Http',
          name: 'mainHttpListener',
          frontendIpConfigurationName: 'myFrontendIpConfiguration',
          frontendPortName: 'myFrontendPort',
        },
      ],
      backendHttpSettings: [
        {
          name: 'mainBackendHttpSettings',
          cookieBasedAffinity: 'Disabled',
          port: 80,
          protocol: 'Http',
          requestTimeout: 20,
          pickHostNameFromBackendAddress: true,
        },
      ],
      urlPathMap: [
        {
          name: 'mainUrlPathMap',
          defaultBackendAddressPoolName: 'functionManagementBackendAddressPool',
          defaultBackendHttpSettingsName: 'mainBackendHttpSettings',
          defaultRewriteRuleSetName: 'mainRewriteRuleSet',
          pathRule: [
            {
              name: 'graphQLRule',
              paths: [`/${environment}/graphql/*`],
              backendAddressPoolName: 'functionManagementBackendAddressPool',
              backendHttpSettingsName: 'mainBackendHttpSettings',
            },
            {
              name: 'sensorRule',
              paths: [`/${environment}/sensor`],
              backendAddressPoolName: 'functionManagementBackendAddressPool',
              backendHttpSettingsName: 'mainBackendHttpSettings',
            },
          ],
        },
      ],
      rewriteRuleSet: [
        {
          name: 'mainRewriteRuleSet',
          rewriteRule: [
            {
              name: 'graphQLRewriteRule',
              ruleSequence: 100,
              condition: [
                {
                  pattern: `/${environment}/graphql`,
                  variable: 'var_request_uri',
                  ignoreCase: true,
                },
              ],
              url: {
                path: '/api/graphql',
                components: 'path_only',
              },
            },
            {
              name: 'sensorRewriteRule',
              ruleSequence: 101,
              condition: [
                {
                  pattern: `/${environment}/sensor/health(/.*)?`,
                  variable: 'var_request_uri',
                  ignoreCase: true,
                },
              ],
              url: {
                path: '/api/sensor/health{var_request_uri_1}',
                components: 'path_only',
              },
            },
          ],
        },
      ],
      requestRoutingRule: [
        {
          name: 'mainRequestRoutingRule',
          ruleType: 'PathBasedRouting',
          httpListenerName: 'mainHttpListener',
          backendAddressPoolName: 'functionManagementBackendAddressPool',
          backendHttpSettingsName: 'mainBackendHttpSettings',
          priority: 100,
          urlPathMapName: 'mainUrlPathMap',
          rewriteRuleSetName: 'mainRewriteRuleSet',
        },
      ],
    })
  }
}
