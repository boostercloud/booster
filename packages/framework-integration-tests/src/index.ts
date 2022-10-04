import { Booster } from '@boostercloud/framework-core'
export {
  Booster,
  boosterEventDispatcher,
  boosterServeGraphQL,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommand,
  boosterRocketDispatcher,
} from '@boostercloud/framework-core'

// eslint-disable-next-line unicorn/prefer-module
Booster.start(__dirname)
