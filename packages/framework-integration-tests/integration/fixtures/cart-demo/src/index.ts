import { Booster } from '@boostercloud/framework-core'
export {
  Booster,
  boosterEventDispatcher,
  boosterProduceEventStream,
  boosterConsumeEventStream,
  boosterServeGraphQL,
  boosterNotifySubscribers,
  boosterHealth,
  boosterTriggerScheduledCommand,
  boosterRocketDispatcher,
} from '@boostercloud/framework-core'

Booster.start(__dirname)
