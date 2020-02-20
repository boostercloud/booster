import { BoosterConfig } from '@boostercloud/framework-types'

export class BoosterReadModelFetcher {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  public static fetch(rawMessage: any, config: BoosterConfig): Promise<any> {
    const provider = config.provider
    return provider.processReadModelAPICall(config, rawMessage)
  }
}
