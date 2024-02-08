import { Booster } from '@boostercloud/framework-core'
import { generateConfig } from '@boostercloud/framework-core/dist/components'
import { Nexus } from '@boostercloud/framework-types/dist/components'
import { NodeContext, Runtime } from '@effect/platform-node'
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

const commands = [generateConfig] as const
const runMain = Runtime.runMain
const contextProvider = NodeContext.layer

const nexus: Nexus = { commands, runMain, contextProvider }

Booster.start(__dirname, nexus)
