export const template = `import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Provider } from '{{{providerPackageName}}}'

Booster.configure('production', (config: BoosterConfig): void => {
  config.appName = '{{{ projectName }}}'
  config.provider = Provider
})
`
