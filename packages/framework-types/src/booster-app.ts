import { BoosterConfig, UUID, EntityInterface } from '.'

/**
 * `BoosterApp` is the interface of the user-facing functions that
 * the framework provides.
 */
export interface BoosterApp {
  start(): void
  configure(configurator: (config: BoosterConfig) => void): void
  fetchEntitySnapshot(entityName: string, entityID: UUID): Promise<EntityInterface | null>
}
