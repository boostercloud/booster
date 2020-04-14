import * as boosterModule from './booster'
import { BoosterApp } from '@boostercloud/framework-types'
export * from './decorators'
export {
  boosterCommandDispatcher,
  boosterReadModelMapper,
  boosterEventDispatcher,
  boosterPreSignUpChecker,
  boosterServeGraphQL,
  boosterRequestAuthorizer,
  boosterDispatchSubscription,
} from './booster'

export const Booster: BoosterApp = boosterModule.Booster
