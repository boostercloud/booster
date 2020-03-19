import * as boosterModule from './booster'
import { BoosterConfig, EntityInterface, UUID, Class } from '@boostercloud/framework-types'
export * from './decorators'
export { Register } from '@boostercloud/framework-types'
export {
  boosterCommandDispatcher,
  boosterReadModelMapper,
  boosterEventDispatcher,
  boosterPreSignUpChecker,
} from './booster'

export interface BoosterApp {
  start(): void

  configure(environment: string, configurator: (config: BoosterConfig) => void): void

  configureCurrentEnv(configurator: (config: BoosterConfig) => void): void

  fetchEntitySnapshot<TEntity extends EntityInterface>(
    entityClass: Class<TEntity>,
    entityID: UUID
  ): Promise<TEntity | undefined>
}

export const Booster: BoosterApp = boosterModule.Booster
