import { ConfigurationProvider, ConfigurationResolver, ConfigurationResolution } from './config'

export class DefaultConfigurationResolver implements ConfigurationResolver {
  private providers: ConfigurationProvider[] = []

  constructor(providers: ConfigurationProvider[] = []) {
    this.providers = [...providers].sort((a, b) => b.priority - a.priority)
  }

  addProvider(provider: ConfigurationProvider): void {
    // Remove any existing provider with the same name
    const existingIndex = this.providers.findIndex((p) => p.name === provider.name)
    if (existingIndex >= 0) {
      this.providers.splice(existingIndex, 1)
    }

    // Add the new provider and sort by priority (highest first)
    this.providers.push(provider)
    this.providers.sort((a, b) => b.priority - a.priority)
  }

  getProviders(): ConfigurationProvider[] {
    return [...this.providers]
  }

  async resolve(key: string): Promise<ConfigurationResolution> {
    // Try each provider in priority order
    for (const provider of this.providers) {
      try {
        // Check if provider is available before attempting to get value
        if (await provider.isAvailable()) {
          const value = await provider.getValue(key)
          if (value !== undefined) {
            return { value, source: provider.name, key }
          }
        }
      } catch (error) {
        // Log error but continue to next provider
        console.warn(`Configuration provider '${provider.name}' failed to resolve key '${key}':`, error)
      }
    }

    // No provider could resolve the value
    return { value: undefined, source: 'none', key }
  }
}

/**
 * Environment variables configuration provider
 * This is the fallback provider that reads from process.env
 */
export class EnvironmentVariablesProvider implements ConfigurationProvider {
  readonly name = 'environment-variables'
  readonly priority = 0 // Lowest priority - fallback provider

  async getValue(key: string): Promise<string | undefined> {
    return process.env[key]
  }

  async isAvailable(): Promise<boolean> {
    return true // Environment variables are always available
  }
}

/**
 * Booster config.env provider
 * Reads from the Booster configuration env object
 */
export class BoosterConfigEnvProvider implements ConfigurationProvider {
  readonly name = 'booster-config-env'
  readonly priority = 10 // Medium priority

  constructor(private readonly envConfig: Record<string, string>) {}

  async getValue(key: string): Promise<string | undefined> {
    return this.envConfig[key]
  }

  async isAvailable(): Promise<boolean> {
    return true // Booster config env is always available
  }
}
