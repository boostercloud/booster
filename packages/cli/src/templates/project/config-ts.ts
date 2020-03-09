export const template = `import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import * as Local from '@boostercloud/framework-provider-local'
import { Provider } from '{{{providerPackageName}}}'

Booster.configure((config: BoosterConfig): void => {
  config.appName = '{{{ projectName }}}'
  config.environments = {
    production: {
      provider: Provider
    },
    development: {
      provider: Local.Provider
    }
  }
})
`
