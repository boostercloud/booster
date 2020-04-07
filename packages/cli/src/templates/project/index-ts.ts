export const template = `import { Booster } from '@boostercloud/framework-core'
export {
  Booster,
  boosterCommandDispatcher,
  boosterReadModelMapper,
  boosterEventDispatcher,
  boosterPreSignUpChecker,
  boosterServeGraphQL,
  boosterRequestAuthorizer,
} from '@boostercloud/framework-core'

Booster.start()
`
