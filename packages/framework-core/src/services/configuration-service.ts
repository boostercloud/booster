import {
  BoosterConfig,
  BoosterConfigEnvProvider,
  ConfigurationProvider,
  ConfigurationResolution,
  ConfigurationResolver,
  DefaultConfigurationResolver,
  EnvironmentVariablesProvider,
} from '@boostercloud/framework-types'

export class ConfigurationService {
  private static instance: ConfigurationService | undefined
  private resolver: ConfigurationResolver

  private constructor(config: BoosterConfig) {
    this.resolver = new DefaultConfigurationResolver()

    // Add default providers (these are always available)
    this.resolver.addProvider(new EnvironmentVariablesProvider())
    this.resolver.addProvider(new BoosterConfigEnvProvider(config.env))

    // Add any registered configuration providers from the config
    for (const provider of config.configurationProviders) {
      this.resolver.addProvider(provider)
    }
  }

  /**
   * Get the singleton instance of the ConfigurationService
   */
  public static getInstance(config: BoosterConfig): ConfigurationService {
    if (!this.instance) {
      this.instance = new ConfigurationService(config)
    }
    return this.instance
  }

  /**
   * Reset the singleton instance (useful for testing)
   */
  public static reset(): void {
    this.instance = undefined
  }

  /**
   * Resolve a configuration value from all available providers
   * @param key The configuration key to resolve
   * @returns Promise resolving to the configuration value or undefined if not found
   */
  public async getValue(key: string): Promise<string | undefined> {
    const resolution = await this.resolver.resolve(key)
    return resolution.value
  }

  /**
   * Resolve a configuration value with source tracking
   * @param key The configuration key to resolve
   * @returns Promise resolving to the full configuration resolution result
   */
  public async resolve(key: string): Promise<ConfigurationResolution> {
    return this.resolver.resolve(key)
  }

  /**
   * Get all registered providers
   */
  public getProviders(): ConfigurationProvider[] {
    return this.resolver.getProviders()
  }
}

/**
 * Utility function to resolve a configuration value using the Booster configuration
 * This is the main API for configuration resolution within the framework
 */
export async function resolveConfigurationValue(config: BoosterConfig, key: string): Promise<string | undefined> {
  const configService = ConfigurationService.getInstance(config)
  return configService.getValue(key)
}

/**
 * Utility function to resolve a configuration value with source tracking
 */
export async function resolveConfigurationWithSource(
  config: BoosterConfig,
  key: string
): Promise<ConfigurationResolution> {
  const configService = ConfigurationService.getInstance(config)
  return configService.resolve(key)
}
