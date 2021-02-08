import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Provider } from '@boostercloud/framework-provider-aws'

Booster.configure('production', (config: BoosterConfig): void => {
  const appNameSuffix = process.env.BOOSTER_APP_SUFFIX ?? 'default'

  config.env['BOOSTER_APP_SUFFIX'] = appNameSuffix

  config.appName = 'my-store-' + appNameSuffix
  config.provider = Provider([
    {
      packageName: '@boostercloud/rocket-auth-aws-infrastructure',
      parameters: {
        mode: 'UserPassword',
      },
    },
  ])
})
