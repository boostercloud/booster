import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import * as AwsProvider from '@boostercloud/framework-provider-aws'
import * as LocalProvider from '@boostercloud/framework-provider-local'

Booster.configure('local', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.provider = LocalProvider.Provider
})

Booster.configure('development', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.provider = AwsProvider.Provider
})

Booster.configure('production', (config: BoosterConfig): void => {
  /* We use an automatically generated app name suffix to allow
   * running integration tests for different branches concurrently.
   */
  const appNameSuffix = process.env.BOOSTER_APP_SUFFIX ?? 'default'

  // The app suffix must be copied to the test app lambdas
  config.env['BOOSTER_APP_SUFFIX'] = appNameSuffix

  config.appName = 'my-store-' + appNameSuffix
  config.provider = AwsProvider.Provider
})
