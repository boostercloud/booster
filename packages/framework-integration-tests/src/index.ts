import { Booster } from '@boostercloud/framework-core'
import nexus from './config/nexus'
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

Booster.withNexus(nexus).start(__dirname)
