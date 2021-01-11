import * as boosterModule from './booster'
import { BoosterApp } from '@boostercloud/framework-types'
export * from './decorators'
export {
  boosterEventDispatcher,
  boosterServeGraphQL,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommand,
} from './booster'

export const Booster: BoosterApp = boosterModule.Booster
