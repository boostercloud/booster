import {
  BoosterConfig,
  EventEnvelope,
  EventDeleteParameters,
  FilterFor,
  SnapshotDeleteParameters,
  UUID,
  EventEnvelopeFromDatabase,
  EntitySnapshotEnvelopeFromDatabase,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { CosmosClient } from '@azure/cosmos'
import { replaceOrDeleteItem, search } from '../helpers/query-helper'

export async function findDeletableEvent(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  parameters: EventDeleteParameters
): Promise<Array<EventEnvelopeFromDatabase>> {
  const logger = getLogger(config, 'events-delete-adapter#findDeletableEvent')
  const stringifyParameters = JSON.stringify(parameters)
  logger.debug(`Initiating a deletable event search for ${stringifyParameters}`)

  const eventStore = config.resourceNames.eventsStore
  const filter = buildDeleteEventFilter(parameters.entityTypeName, parameters.entityID, parameters.createdAt)
  const result = (await search(cosmosDb, config, eventStore, filter)) as Array<EventEnvelopeFromDatabase>
  logger.debug(`Finished deletable event search for ${stringifyParameters}`)
  return result
}

export async function findDeletableSnapshot(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  parameters: SnapshotDeleteParameters
): Promise<Array<EntitySnapshotEnvelopeFromDatabase>> {
  const logger = getLogger(config, 'events-delete-adapter#findDeletableSnapshot')
  const stringifyParameters = JSON.stringify(parameters)
  logger.debug(`Initiating a deletable snapshot search for ${stringifyParameters}`)

  const eventStore = config.resourceNames.eventsStore
  const filter = buildDeleteEntityFilter(parameters.entityTypeName, parameters.entityID, parameters.createdAt)
  const result = (await search(cosmosDb, config, eventStore, filter)) as Array<EntitySnapshotEnvelopeFromDatabase>
  logger.debug(`Finished deletable snapshot search for ${stringifyParameters}`)
  return result
}

export async function deleteEvent(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  events: Array<EventEnvelopeFromDatabase>
): Promise<void> {
  const logger = getLogger(config, 'events-delete-adapter#deleteEvent')
  const stringifyParameters = JSON.stringify(events)
  logger.debug(`Initiating an event delete for ${stringifyParameters}`)

  const eventStore = config.resourceNames.eventsStore
  if (!events || events.length === 0) {
    logger.warn('Could not find events to delete')
    return
  }
  for (const event of events) {
    const newEvent = buildNewEvent(event)
    const partitionKey = partitionKeyBuilder(event.entityTypeName, event.entityID, 'event')
    await replaceOrDeleteItem(cosmosDb, eventStore, config, event.id, partitionKey, newEvent)
  }
  logger.debug(`Finished event delete for ${stringifyParameters}`)
}

export async function deleteSnapshot(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  snapshots: Array<EntitySnapshotEnvelopeFromDatabase>
): Promise<void> {
  const logger = getLogger(config, 'events-delete-adapter#deleteSnapshot')
  const stringifyParameters = JSON.stringify(snapshots)
  logger.debug(`Initiating a snapshot delete for ${stringifyParameters}`)

  const eventStore = config.resourceNames.eventsStore
  if (!snapshots || snapshots.length === 0) {
    logger.warn('Could not find snapshot to delete')
    return
  }
  for (const snapshot of snapshots) {
    const partitionKey = partitionKeyBuilder(snapshot.entityTypeName, snapshot.entityID, 'snapshot')
    await replaceOrDeleteItem(cosmosDb, eventStore, config, snapshot.id, partitionKey)
  }
  logger.debug(`Finished snapshot delete for ${stringifyParameters}`)
}

interface DeleteQueryFields {
  createdAt: string
  entityTypeName_entityID_kind: string
  kind: string
  deletedAt: string
}

function buildNewEvent(existingEvent: EventEnvelopeFromDatabase): EventEnvelope {
  return {
    ...existingEvent,
    deletedAt: new Date().toISOString(),
    value: {},
  }
}

function buildDeleteEventFilter(
  entityTypeName: string,
  entityId: UUID,
  createdAt: string
): FilterFor<DeleteQueryFields> {
  const value = `${entityTypeName}-${entityId}-event`
  return {
    entityTypeName_entityID_kind: { eq: value },
    createdAt: { eq: createdAt },
    kind: { eq: 'event' },
    deletedAt: { isDefined: false },
  }
}

function buildDeleteEntityFilter(
  entityTypeName: string,
  entityId: UUID,
  createdAt: string
): FilterFor<DeleteQueryFields> {
  const value = `${entityTypeName}-${entityId}-snapshot`
  return {
    entityTypeName_entityID_kind: { eq: value },
    kind: { eq: 'snapshot' },
    createdAt: { eq: createdAt },
    deletedAt: { isDefined: false },
  }
}

function partitionKeyBuilder(entityTypeName: string, entityID: UUID, kind: 'event' | 'snapshot'): string {
  return `${entityTypeName}-${entityID}-${kind}`
}
