export const template = `import { Booster } from '@boostercloud/framework-core'
export {
  Booster,
  boosterEventDispatcher,
  boosterProduceEventStream,
  boosterConsumeEventStream,
  boosterServeGraphQL,
  boosterNotifySubscribers,
  boosterHealth,
  boosterTriggerScheduledCommands,
  boosterRocketDispatcher,
} from '@boostercloud/framework-core'

Booster.start(__dirname)
`
