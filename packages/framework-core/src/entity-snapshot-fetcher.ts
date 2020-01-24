import { UUID, BoosterConfig, EntityInterface, Logger } from '@boostercloud/framework-types'
import { EventStore } from './services/event-store'

export async function fetchEntitySnapshot(
  config: BoosterConfig,
  logger: Logger,
  entityName: string,
  entityID: UUID
): Promise<EntityInterface | null> {
  const eventStore = new EventStore(config, logger)
  const entitySnapshotEnvelope = await eventStore.fetchEntitySnapshot(entityName, entityID)
  return entitySnapshotEnvelope?.value as EntityInterface
}
