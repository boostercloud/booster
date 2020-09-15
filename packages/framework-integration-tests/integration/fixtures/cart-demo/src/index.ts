import { Booster } from '@boostercloud/framework-core'
export {
  Booster,
  boosterEventDispatcher,
  boosterPreSignUpChecker,
  boosterServeGraphQL,
  boosterNotifySubscribers,
  boosterScheduleTask,
} from '@boostercloud/framework-core'

Booster.start()
