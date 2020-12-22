import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import * as AWS from '@boostercloud/framework-provider-aws'

// TODO: After prunning `devDependencies` to deploy the project to lambda, we cannot import the local provider anymore. We should look for a better solution than this, maybe loading a different config file depending on the configured environment in BOOSTER_ENV
if (process.env.BOOSTER_ENV === 'local') {
  const Local = require('@boostercloud/framework-provider-local')

  Booster.configure('local', (config: BoosterConfig): void => {
    config.appName = 'my-store'
    config.provider = Local.Provider
  })
}

Booster.configure('development', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.provider = AWS.Provider
})

Booster.configure('production', (config: BoosterConfig): void => {
  /* We use an automatically generated app name suffix to allow
   * running integration tests for different branches concurrently.
   */
  const appNameSuffix = process.env.BOOSTER_APP_SUFFIX ?? 'default'

  // The app suffix must be copied to the test app lambdas
  config.env['BOOSTER_APP_SUFFIX'] = appNameSuffix

  config.appName = 'my-store-' + appNameSuffix
  config.provider = AWS.Provider
})
