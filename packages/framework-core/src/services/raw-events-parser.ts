import { BoosterConfig, EventEnvelope, UUID } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'

export type EventsStreamingCallback = (
  entityName: string,
  entityID: UUID,
  eventEnvelopes: Array<EventEnvelope>,
  config: BoosterConfig
) => Promise<void>
type EnvelopesPerEntity = Record<string, Array<EventEnvelope>>

export class RawEventsParser {
  public static async streamPerEntityEvents(
    config: BoosterConfig,
    eventEnvelopes: Array<EventEnvelope>,
    callbackFn: EventsStreamingCallback
  ): Promise<void> {
    const logger = getLogger(config, 'RawEventsParser#streamPerEntityEvents')
    const eventEnvelopesPerEntity = eventEnvelopes
      .filter(isEventKind)
      .filter(isNotDeleted)
      .reduce(groupByEntity, {})

    const processes = Object.values(eventEnvelopesPerEntity).map(async (entityEnvelopes) => {
      // All envelopes are for the same entity type/ID, so we get the first one to get those values
      if (!entityEnvelopes[0]) {
        throw new Error('The impossible happened: Attempted to process a non existent event')
      }
      const { entityTypeName, entityID } = entityEnvelopes[0]
      logger.debug(
        `Streaming the following events for entity '${entityTypeName}' and ID '${entityID}':`,
        entityEnvelopes
      )
      try {
        await callbackFn(entityTypeName, entityID, entityEnvelopes, config)
      } catch (e) {
        logger.error(`An error occurred while processing events for entity ${entityTypeName} with ID ${entityID}`, e)
      }
    })
    // We use allSettled because we don't care if some of the processes fail
    await Promise.allSettled(processes)
  }
}

function isEventKind(envelope: EventEnvelope): boolean {
  return envelope.kind == 'event'
}

function isNotDeleted(envelope: EventEnvelope): boolean {
  return !envelope?.deletedAt
}

function groupByEntity(envelopesPerEntity: EnvelopesPerEntity, envelope: EventEnvelope): EnvelopesPerEntity {
  const entityKey = `${envelope.entityTypeName}-${envelope.entityID}`
  if (!envelopesPerEntity[entityKey]) {
    envelopesPerEntity[entityKey] = []
  }
  envelopesPerEntity[entityKey].push(envelope)
  return envelopesPerEntity
}
