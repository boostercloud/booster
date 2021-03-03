import * as boosterModule from './booster'
import { BoosterApp } from '@boostercloud/framework-types'
import './components'
export * from './decorators'
export {
  boosterEventDispatcher,
  boosterPreSignUpChecker,
  boosterServeGraphQL,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommand,
} from './booster'

export const Booster: BoosterApp = boosterModule.Booster
