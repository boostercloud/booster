import * as boosterModule from './booster'
import { BoosterApp } from '@boostercloud/framework-types'

// Core entry points
export {
  boosterEventDispatcher,
  boosterServeGraphQL,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommand,
  boosterRocketDispatcher,
} from './booster'

// User-facing Booster SDK
export * from './sdk'

// The Booster singleton class
export const Booster: BoosterApp = boosterModule.Booster
