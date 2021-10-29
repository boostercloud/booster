import { ProviderContext } from '@boostercloud/framework-types'

export class ProviderRegistry {
  private static providers: Record<string, ProviderContext> = {}

  public static register(name: string, providerContext: ProviderContext): void {
    ProviderRegistry.providers[name] = providerContext
  }

  public static get(name: string): ProviderContext {
    return ProviderRegistry.providers[name]
  }
}
