export const template = `import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'

Booster.configure((config: BoosterConfig): void => {
  config.appName = '{{{ projectName }}}'
})
`
