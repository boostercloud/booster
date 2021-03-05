import { Booster } from '@boostercloud/framework-core'
export {
  Booster,
  boosterEventDispatcher,
  boosterPreSignUpChecker,
  boosterServeGraphQL,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommand,
} from '@boostercloud/framework-core'

Booster.start(__dirname)

//Booster.config.projections['Cart'][0].hasBeenProjected = true
console.log('///// PROJECTIONS /////')
console.log(Booster.config.projections)
