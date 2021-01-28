import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Provider } from '@boostercloud/framework-provider-aws'

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'boosted-blog'
  config.provider = Provider
})

Booster.configure('fake_environment', (config: BoosterConfig): void => {
  config.appName = 'boosted-blog-fake'
  config.provider = Provider
})
