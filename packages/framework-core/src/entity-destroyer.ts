import { UUID, BoosterConfig, EntityInterface, Logger, Class } from '@boostercloud/framework-types'
import { EventStore } from './services/event-store'

export async function destroyEntity<TEntity extends EntityInterface>(
  config: BoosterConfig,
  logger: Logger,
  entityClass: Class<TEntity>,
  entityID: UUID
): Promise<void> {
  const eventStore = new EventStore(config, logger)
  return await eventStore.destroyEntity(entityClass.name, entityID)
}
