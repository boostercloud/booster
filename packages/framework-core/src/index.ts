import * as boosterModule from './booster'
import { BoosterApp } from '@boostercloud/framework-types'
export * from './decorators'
export {
  boosterEventDispatcher,
  boosterPreSignUpChecker,
  boosterServeGraphQL,
  boosterNotifySubscribers,
} from './booster'

export const Booster: BoosterApp = boosterModule.Booster
