import * as boosterModule from './booster'
import { BoosterApp } from '@boostercloud/framework-types'
export { RegisterHandler } from './booster-register-handler'
export * from './decorators'
export { BoosterDataMigrations } from './booster-data-migrations'
export { BoosterDataMigrationFinished } from './core-concepts/data-migration/events/booster-data-migration-finished'
export { BoosterDataMigrationEntity } from './core-concepts/data-migration/entities/booster-data-migration-entity'
export { BoosterTouchEntityHandler } from './booster-touch-entity-handler'
export {
  boosterEventDispatcher,
  boosterServeGraphQL,
  boosterNotifySubscribers,
  boosterTriggerScheduledCommand,
  boosterRocketDispatcher,
  boosterHealth,
} from './booster'
export * from './services/token-verifiers'
export * from './instrumentation/index'
export * from './decorators/health-sensor'

export const Booster: BoosterApp = boosterModule.Booster
