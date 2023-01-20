import * as boosterModule from './booster'
import { BoosterApp } from '@boostercloud/framework-types'
export { RegisterHandler } from './booster-register-handler'
export * from './decorators'
export { BoosterDataMigrations } from './booster-data-migrations'
export { BoosterDataMigrationFinished } from './core-concepts/data-migration/events/booster-data-migration-finished'
export { BoosterDataMigrationEntity } from './core-concepts/data-migration/entities/booster-data-migration-entity'
export {
  boosterEventDispatcher,
  boosterServeGraphQL,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommand,
  boosterRocketDispatcher,
} from './booster'
export * from './services/token-verifiers'

export const Booster: BoosterApp = boosterModule.Booster
