import * as boosterModule from './booster'
import { BoosterApp } from '@boostercloud/framework-types'
export * from './decorators'
export { Register } from '@boostercloud/framework-types'
export {
  boosterCommandDispatcher,
  boosterReadModelMapper,
  boosterEventDispatcher,
  boosterPreSignUpChecker,
} from './booster'

export const Booster: BoosterApp = boosterModule.Booster
