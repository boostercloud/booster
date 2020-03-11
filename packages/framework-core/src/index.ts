import * as boosterModule from './booster'
import { BoosterConfig, EntityInterface, UUID } from '@boostercloud/framework-types'
export * from './decorators'
export { Register } from '@boostercloud/framework-types'
export {
  boosterCommandDispatcher,
  boosterReadModelMapper,
  boosterEventDispatcher,
  boosterPreSignUpChecker,
} from './booster'

interface BoosterApp {
  start(): void

  configure(configurator: (config: BoosterConfig) => void): void
  environment(environment: string, configurator: (config: BoosterConfig) => void): void

  fetchEntitySnapshot(entityName: string, entityID: UUID): Promise<EntityInterface | null>
}

export const Booster: BoosterApp = boosterModule.Booster
