import { Booster } from '@boostercloud/framework-core'
export {
  Booster,
  boosterCommandDispatcher,
  boosterReadModelMapper,
  boosterEventDispatcher,
  boosterPreSignUpChecker,
  boosterServeGraphQL,
} from '@boostercloud/framework-core'

Booster.start()
