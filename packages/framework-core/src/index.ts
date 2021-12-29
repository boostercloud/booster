import * as boosterModule from './booster'
import { BoosterApp } from '@boostercloud/framework-types'
export { RegisterHandler } from './booster-register-handler'
export * from './decorators'
export {
  boosterEventDispatcher,
  boosterServeGraphQL,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommand,
  boosterRocketDispatcher,
} from './booster'

export const Booster: BoosterApp = boosterModule.Booster
