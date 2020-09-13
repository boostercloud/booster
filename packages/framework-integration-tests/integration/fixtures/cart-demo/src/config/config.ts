import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import { AWSProvider } from '@boostercloud/framework-provider-aws'

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = 'project_name_fixture_placeholder'
  config.provider = AWSProvider([])
})
