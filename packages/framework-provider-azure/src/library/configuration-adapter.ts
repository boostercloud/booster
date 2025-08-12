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
   * @returns true if initialization was successful, false otherwise
   */
  private async initialize(): Promise<boolean> {
    if (this.isInitialized) {
      return !this.initializationError
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
        this.initializationError = new Error(
          'Azure App Configuration requires either a connection string or endpoint URL'
        )
        this.isInitialized = true
        return false
      }

      this.isInitialized = true
      return true
    } catch (error) {
      // Preserve original error information by wrapping it
      const originalError = error instanceof Error ? error : new Error(String(error))
      this.initializationError = new Error(
        `Failed to initialize Azure App Configuration client: ${originalError.message}`
      )
      // Preserve the original error as a property for debugging
      ;(this.initializationError as any).originalError = originalError
      this.isInitialized = true // Mark as initialized to avoid retrying
      return false
    }
  }

  async getValue(key: string): Promise<string | undefined> {
    const initialized = await this.initialize()

    if (!initialized || !this.client) {
      return undefined
    }

    try {
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
    const initialized = await this.initialize()
    return initialized && !!this.client && !this.initializationError
  }

  /**
   * Create a ConfigurationAdapter instance from environment variables
   * This is the standard way to initialize the provider in Azure Function App environments,
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
