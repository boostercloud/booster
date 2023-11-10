import { Booster } from '@boostercloud/framework-core'
export {
  Booster,
  boosterEventDispatcher,
  boosterPreSignUpChecker,
  boosterHealth,
  boosterServeGraphQL,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommand,
} from '@boostercloud/framework-core'

Booster.start(__dirname)
