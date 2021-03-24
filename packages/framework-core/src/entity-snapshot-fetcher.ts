import { UUID, BoosterConfig, EntityInterface, Logger, Class } from '@boostercloud/framework-types'
import { EventStore } from './services/event-store'

export async function fetchEntitySnapshot<TEntity extends EntityInterface>(
  config: BoosterConfig,
  logger: Logger,
  entityClass: Class<TEntity>,
  entityID: UUID,
  at?: Date
): Promise<TEntity | undefined> {
  const eventStore = new EventStore(config, logger)
  const entitySnapshotEnvelope = await eventStore.fetchEntitySnapshot(entityClass.name, entityID, at?.toISOString())
  return entitySnapshotEnvelope?.value as TEntity
}
