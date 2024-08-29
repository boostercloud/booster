import {
  BoosterConfig,
  EventEnvelope,
  EventDeleteParameters,
  SnapshotDeleteParameters,
  UUID,
  EventEnvelopeFromDatabase,
  EntitySnapshotEnvelopeFromDatabase,
  EntitySnapshotEnvelope,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { EventRegistry } from '../services'
import { QueryOperation, QueryValue } from './searcher-adapter'

type DatabaseEventEnvelopeWithId = EventEnvelope & { _id: string }
type DatabaseEntitySnapshotEnvelopeWithId = EntitySnapshotEnvelope & { _id: string }

export async function findDeletableEvent(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  parameters: EventDeleteParameters
): Promise<Array<EventEnvelopeFromDatabase>> {
  const logger = getLogger(config, 'events-delete-adapter#findDeletableEvent')
  const stringifyParameters = JSON.stringify(parameters)
  logger.debug(`Initiating a deletable event search for ${stringifyParameters}`)

  const filter = buildDeleteEventFilter(parameters.entityTypeName, parameters.entityID, parameters.createdAt)
  const events = (await eventRegistry.query(filter)) as Array<DatabaseEventEnvelopeWithId>
  const result = events.map((event) => {
    return {
      ...event,
      id: event._id,
    }
  }) as Array<EventEnvelopeFromDatabase>
  logger.debug(`Finished deletable event search for ${stringifyParameters}`)
  return result
}

export async function findDeletableSnapshot(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  parameters: SnapshotDeleteParameters
): Promise<Array<EntitySnapshotEnvelopeFromDatabase>> {
  const logger = getLogger(config, 'events-delete-adapter#findDeletableSnapshot')
  const stringifyParameters = JSON.stringify(parameters)
  logger.debug(`Initiating a deletable snapshot search for ${stringifyParameters}`)

  const filter = buildDeleteEntityFilter(parameters.entityTypeName, parameters.entityID, parameters.createdAt)
  const snapshots = (await eventRegistry.query(filter)) as Array<DatabaseEntitySnapshotEnvelopeWithId>
  const result: Array<EntitySnapshotEnvelopeFromDatabase> = snapshots.map((snapshot) => {
    return {
      ...snapshot,
      id: snapshot._id,
    }
  })
  logger.debug(`Finished deletable snapshot search for ${stringifyParameters}`)
  return result
}

export async function deleteEvent(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  events: Array<EventEnvelopeFromDatabase>
): Promise<void> {
  const logger = getLogger(config, 'events-delete-adapter#deleteEvent')
  const stringifyParameters = JSON.stringify(events)
  logger.debug(`Initiating an event delete for ${stringifyParameters}`)

  if (!events || events.length === 0) {
    logger.warn('Could not find events to delete')
    return
  }
  for (const event of events) {
    const newEvent = buildNewEvent(event)
    await eventRegistry.replaceOrDeleteItem(event.id, newEvent)
  }
  logger.debug(`Finished event delete for ${stringifyParameters}`)
}

export async function deleteSnapshot(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  snapshots: Array<EntitySnapshotEnvelopeFromDatabase>
): Promise<void> {
  const logger = getLogger(config, 'events-delete-adapter#deleteSnapshot')
  const stringifyParameters = JSON.stringify(snapshots)
  logger.debug(`Initiating a snapshot delete for ${stringifyParameters}`)

  if (!snapshots || snapshots.length === 0) {
    logger.warn('Could not find snapshot to delete')
    return
  }
  for (const snapshot of snapshots) {
    await eventRegistry.replaceOrDeleteItem(snapshot.id)
  }
  logger.debug(`Finished snapshot delete for ${stringifyParameters}`)
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
): Record<string, QueryOperation<QueryValue>> {
  return {
    entityID: entityId.toString(),
    entityTypeName: entityTypeName,
    createdAt: createdAt,
    kind: 'event',
    deletedAt: { $exists: false },
  }
}

function buildDeleteEntityFilter(
  entityTypeName: string,
  entityId: UUID,
  createdAt: string
): Record<string, QueryOperation<QueryValue>> {
  return {
    entityTypeName: entityTypeName,
    entityID: entityId.toString(),
    kind: 'snapshot',
    createdAt: createdAt,
    deletedAt: { $exists: false },
  }
}
