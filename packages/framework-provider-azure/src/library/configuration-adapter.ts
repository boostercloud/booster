import { ConfigurationProvider } from '@boostercloud/framework-types'
import { AppConfigurationClient } from '@azure/app-configuration'
import { DefaultAzureCredential } from '@azure/identity'

export class ConfigurationAdapter implements ConfigurationProvider {
  readonly name = 'azure-app-configuration'
  readonly priority = 20 // High priority - external configuration source

  private client: AppConfigurationClient | undefined
  private isInitialized = false
  private initializationError: Error | undefined

  constructor(
    private readonly connectionString?: string,
    private readonly endpoint?: string,
    private readonly labelFilter?: string
  ) {}

  /**
   * Initialize the Azure App Configuration client
   */
  private async initialize(): Promise<void> {
    if (this.isInitialized) {
      return
    }

    try {
      if (this.connectionString) {
        // Use connection string if provided
        this.client = new AppConfigurationClient(this.connectionString)
      } else if (this.endpoint) {
        // Use managed identity or default Azure credential with endpoint
        const credential = new DefaultAzureCredential()
        this.client = new AppConfigurationClient(this.endpoint, credential)
      } else {
        throw new Error('Azure App Configuration requires either a connection string or endpoint URL')
      }
    } catch (error) {
      this.initializationError = error instanceof Error ? error : new Error(String(error))
      this.isInitialized = true // Mark as initialized to avoid retrying
      throw this.initializationError
    }
  }

  async getValue(key: string): Promise<string | undefined> {
    try {
      await this.initialize()

      if (!this.client) {
        return undefined
      }

      // Get the configuration setting with optional label filter
      const configurationSetting = await this.client.getConfigurationSetting({
        key,
        label: this.labelFilter,
      })

      return configurationSetting.value
    } catch (error) {
      // Log the error but don't throw - this allows fallback to other providers
      console.warn(`Azure App Configuration failed to get the value for key '${key}':`, error)
      return undefined
    }
  }

  async isAvailable(): Promise<boolean> {
    try {
      await this.initialize()
      return !!this.client && !this.initializationError
    } catch (error) {
      return false
    }
  }

  /**
   * Create a ConfigurationAdapter instance from environment variables
   * This if the standard way to initialize the provider in Azure Function App environments,
   * where these environment variables are automatically injected. In other environments,
   * you may need to set these variables manually.
   */
  static fromEnvironment(labelFilter?: string): ConfigurationAdapter {
    const connectionString = process.env['AZURE_APP_CONFIG_CONNECTION_STRING']
    const endpoint = process.env['AZURE_APP_CONFIG_ENDPOINT']

    return new ConfigurationAdapter(connectionString, endpoint, labelFilter)
  }

  /**
   * Create a ConfigurationAdapter instance with connection string
   */
  static withConnectionString(connectionString: string, labelFilter?: string): ConfigurationAdapter {
    return new ConfigurationAdapter(connectionString, undefined, labelFilter)
  }

  /**
   * Create a ConfigurationAdapter instance with endpoint and managed identity
   */
  static withEndpoint(endpoint: string, labelFilter?: string): ConfigurationAdapter {
    return new ConfigurationAdapter(undefined, endpoint, labelFilter)
  }
}

/**
 * Configuration options for Azure App Configuration integration
 */
export interface AzureAppConfigurationOptions {
  /** Connection string for Azure App Configuration (alternative to endpoint + managed identity) */
  connectionString?: string

  /** Endpoint URL for Azure App Configuration (used with managed identity) */
  endpoint?: string

  /** Optional label filter to target specific configuration values */
  labelFilter?: string

  /** Whether to enable Azure App Configuration (default: false) */
  enabled?: boolean
}
