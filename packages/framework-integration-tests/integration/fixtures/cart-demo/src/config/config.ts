import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Provider } from '@boostercloud/framework-provider-aws'

Booster.configureLocal('local', (config: BoosterConfig): void => {
  config.appName = 'project_name_placeholder'
})

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'project_name_placeholder'
  config.provider = Provider()
})
