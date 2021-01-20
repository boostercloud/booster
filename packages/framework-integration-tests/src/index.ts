import { Booster } from '@boostercloud/framework-core'
export {
  Booster,
  boosterEventDispatcher,
  boosterPreSignUpChecker,
  boosterServeGraphQL,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommand,
  boosterGetGraphQLSchema,
} from '@boostercloud/framework-core'

Booster.start()
