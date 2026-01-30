import { Booster } from '@boostercloud/framework-core'
export {
  Booster,
  boosterEventDispatcher,
  boosterServeGraphQL,
  boosterHealth,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommands,
  boosterRocketDispatcher,
  boosterProduceEventStream,
  boosterConsumeEventStream,
} from '@boostercloud/framework-core'

Booster.start(__dirname)
