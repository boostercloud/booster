import { Booster } from '@boostercloud/framework-core'
export {
  Booster,
  boosterEventDispatcher,
  boosterServeGraphQL,
  boosterHealth,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommand,
  boosterRocketDispatcher,
} from '@boostercloud/framework-core'

Booster.start(__dirname)
