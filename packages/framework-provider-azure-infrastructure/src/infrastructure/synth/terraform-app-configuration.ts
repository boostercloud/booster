import { Construct } from 'constructs'
import { appConfiguration } from '@cdktf/provider-azurerm'
import { BoosterConfig } from '@boostercloud/framework-types'
import { ApplicationSynthStack } from '../types/application-synth-stack'
import { toTerraformName } from '../helper/utils'

export class TerraformAppConfiguration extends Construct {
  public readonly appConfiguration: appConfiguration.AppConfiguration

  constructor(scope: Construct, applicationStack: ApplicationSynthStack, config: BoosterConfig) {
    super(scope, 'AppConfiguration')

    const { appPrefix, resourceGroup } = applicationStack

    // Check if Azure App Configuration is enabled in the config
    const azureAppConfigOptions = (config as any)._azureAppConfigOptions
    if (!azureAppConfigOptions?.enabled) {
      // If not eabled, create a placeholder without actual resources
      this.appConfiguration = {} as appConfiguration.AppConfiguration
      return
    }

    const name = toTerraformName(appPrefix, 'appconfig')

    this.appConfiguration = new appConfiguration.AppConfiguration(this, 'AppConfiguration', {
      name,
      resourceGroupName: resourceGroup.name,
      location: resourceGroup.location,
      sku: 'free', // Use free tier by default
      tags: {
        Application: config.appName,
        Environment: config.environmentName,
        BoosterManaged: 'true',
      },
      // Enable managed identity for secure access
      identity: {
        type: 'SystemAssigned',
      },
      // Configure public network access
      publicNetworkAccess: 'Enabled',
      // Configure local authentication
      localAuthEnabled: true,
    })
  }

  /**
   * Get the connection string for the App Configuration resource
   */
  public getConnectionString(): string {
    if (!this.appConfiguration || !this.appConfiguration.primaryWriteKey) {
      return ''
    }
    return `Endpoint=https://${this.appConfiguration.name}.azconfig.io;Id=${
      this.appConfiguration.primaryWriteKey.get(0).id
    };Secret=${this.appConfiguration.primaryWriteKey.get(0).secret}`
  }

  /**
   * Get the endpoint URL for the App Configuration resource
   */
  public getEndpoint(): string {
    if (!this.appConfiguration || !this.appConfiguration.endpoint) {
      return ''
    }
    return this.appConfiguration.endpoint
  }

  /**
   * Check if App Configuration is enabled for this configuration
   */
  public static isEnabled(config: BoosterConfig): boolean {
    const azureAppConfigOptions = (config as any)._azureAppConfigOptions
    return azureAppConfigOptions?.enabled === true
  }
}
