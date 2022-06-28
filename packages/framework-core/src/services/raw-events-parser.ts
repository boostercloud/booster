import { BoosterConfig, EventEnvelope, UUID } from '@boostercloud/framework-types'
import { getLogger, Promises } from '@boostercloud/framework-common-helpers'

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
    rawEvents: unknown,
    callbackFn: EventsStreamingCallback
  ): Promise<void> {
    const eventEnvelopesPerEntity = config.provider.events
      .rawToEnvelopes(rawEvents)
      .filter(isEventKind)
      .reduce(groupByEntity, {})

    const processes = Object.values(eventEnvelopesPerEntity).map(async (entityEnvelopes) => {
      const logger = getLogger(config, 'RawEventsParser#streamPerEntityEvents')
      // All envelopes are for the same entity type/ID, so we get the first one to get those values
      if (!entityEnvelopes[0]) {
        throw new Error('The impossible happened: Attempted to process a non existent event')
      }
      const { entityTypeName, entityID } = entityEnvelopes[0]
      logger.debug(
        `Streaming the following events for entity '${entityTypeName}' and ID '${entityID}':`,
        entityEnvelopes
      )
      await callbackFn(entityTypeName, entityID, entityEnvelopes, config)
    })
    await Promises.allSettledAndFulfilled(processes)
  }
}

function isEventKind(envelope: EventEnvelope): boolean {
  return envelope.kind == 'event'
}

function groupByEntity(envelopesPerEntity: EnvelopesPerEntity, envelope: EventEnvelope): EnvelopesPerEntity {
  const entityKey = `${envelope.entityTypeName}-${envelope.entityID}`
  if (!envelopesPerEntity[entityKey]) {
    envelopesPerEntity[entityKey] = []
  }
  envelopesPerEntity[entityKey].push(envelope)
  return envelopesPerEntity
}
