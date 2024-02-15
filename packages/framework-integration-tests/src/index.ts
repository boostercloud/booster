import { Booster } from '@boostercloud/framework-core'
import injectable from './config/injectable'
export {
  Booster,
  boosterEventDispatcher,
  boosterServeGraphQL,
  boosterHealth,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommand,
  boosterRocketDispatcher,
  boosterProduceEventStream,
  boosterConsumeEventStream,
} from '@boostercloud/framework-core'

Booster.withInjectable(injectable).start(__dirname)
