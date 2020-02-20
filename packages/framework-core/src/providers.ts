// TODO: Remove this builder and replace it with an injection mechanism to invert the dependency of the specific implementation (BOOST-355)
import { Provider, ProviderLibrary, ProviderInfrastructure, BoosterConfig } from '@boostercloud/framework-types'
import { Library as AWSLibrary } from '@boostercloud/framework-provider-aws'
import { Infrastructure as AWSInfrastructure } from '@boostercloud/framework-provider-aws-infrastructure'

class UnsupportedProviderError extends Error {}

export class Providers {
  public static list = [Provider.AWS]
  public static default = Provider.AWS

  /**
   * Get an instance of the current provider runtime
   *
   * @param config Booster configuration
   */
  public static getLibrary(config: BoosterConfig): ProviderLibrary {
    switch (config.provider) {
      case Provider.AWS:
        return AWSLibrary
      default:
        throw Providers.unsupportedProviderError(config.provider)
    }
  }

  public static getInfrastructure(config: BoosterConfig): ProviderInfrastructure {
    switch (config.provider) {
      case Provider.AWS:
        return AWSInfrastructure
      default:
        throw Providers.unsupportedProviderError(config.provider)
    }
  }

  private static unsupportedProviderError(provider: string): UnsupportedProviderError {
    return new UnsupportedProviderError(
      `Provider not supported ${provider}. ` +
        `Supported providers: ${this.list.join(', ')}. ` +
        'Please check your project configuration and try again.'
    )
  }

  /**
   * Method to get a Provider from its string representation
   *
   * @param provider string representation of a provider
   */
  public static choose(provider: string | undefined | null): Promise<Provider> {
    if (!provider) return Promise.resolve(Providers.default)
    switch (provider && provider.toLowerCase()) {
      case 'aws':
        return Promise.resolve(Provider.AWS)
      default:
        return Promise.reject(
          new Error(`Provider not supported ${provider}. Supported providers: ${Providers.list.join(', ')}`)
        )
    }
  }
}
