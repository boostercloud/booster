import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Provider } from '@boostercloud/framework-provider-aws'
import * as Local from '@boostercloud/framework-provider-local'

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.provider = Provider
})

Booster.configure('development', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.provider = Local.Provider
})
