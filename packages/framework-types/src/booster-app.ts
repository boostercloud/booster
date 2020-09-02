import { BoosterConfig, UUID, EntityInterface, Class } from '.'

/**
 * `BoosterApp` is the interface of the user-facing functions that
 * the framework provides.
 */
export interface BoosterApp {
  start(): void
  configure(environment: string, configurator: (config: BoosterConfig) => void): void
  configureCurrentEnv(configurator: (config: BoosterConfig) => void): void
  fetchEntitySnapshot<TEntity extends EntityInterface>(
    entityName: Class<TEntity>,
    entityID: UUID
  ): Promise<TEntity | undefined>
  destroyEntity<TEntity extends EntityInterface>(entityName: Class<TEntity>, entityID: UUID): Promise<void>
}
