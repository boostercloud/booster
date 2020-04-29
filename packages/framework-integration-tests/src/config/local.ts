import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Provider } from '@boostercloud/framework-provider-local'

Booster.configure('local', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.provider = Provider
})
