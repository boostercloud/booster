import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
// import * as Local from '@boostercloud/framework-provider-local'
import { Provider } from '@boostercloud/framework-provider-aws'

Booster.configure((config: BoosterConfig): void => {
  config.appName = 'environments-split-test'
  config.environments = {
    production: {
      provider: Provider,
    },
    development: {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      provider: (undefined as unknown) as any,
    },
  }
})
