import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import { AWSProvider } from '@boostercloud/framework-provider-aws'
import * as Local from '@boostercloud/framework-provider-local'

Booster.configure('local', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.provider = Local.Provider
})

Booster.configure('development', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.provider = AWSProvider()
})

Booster.configure('production', (config: BoosterConfig): void => {
  /* We use an automatically generated app name suffix to allow
   * running integration tests for different branches concurrently.
   */
  const appNameSuffix = process.env.BOOSTER_APP_SUFFIX ?? 'default'

  // The app suffix must be copied to the test app lambdas
  config.env['BOOSTER_APP_SUFFIX'] = appNameSuffix

  config.appName = 'my-store-' + appNameSuffix
  config.provider = AWSProvider()
})
