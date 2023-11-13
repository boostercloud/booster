import { BoosterConfig, EventEnvelope, EventStream } from '@boostercloud/framework-types'
import { Context } from '@azure/functions'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { AZURE_CONFLICT_ERROR_CODE } from '../constants'
import { CosmosClient } from '@azure/cosmos'

interface DedupEventStream {
  primaryKey: string
  createdAt: string
  ttl: number
}

const DEFAULT_DEDUP_TTL = 86400

export async function dedupEventStream(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  context: Context
): Promise<EventStream> {
  const logger = getLogger(config, 'dedup-events-stream#dedupEventsStream')
  const events = (context.bindings.eventHubMessages as EventStream) || []
  logger.debug(`Dedup ${events.length} events`)

  const resources: EventStream = []
  for (const event of events) {
    const rawParsed = JSON.parse(event as string)
    const eventTag: DedupEventStream = {
      primaryKey: rawParsed._etag,
      createdAt: new Date().toISOString(),
      ttl: config.eventStreamConfiguration.parameters?.dedupTtl ?? DEFAULT_DEDUP_TTL,
    }
    try {
      const { resource } = await cosmosDb
        .database(config.resourceNames.applicationStack)
        .container(config.resourceNames.eventsDedup)
        .items.create(eventTag)
      if (resource) {
        resources.push(event)
      }
    } catch (error) {
      if (error.code !== AZURE_CONFLICT_ERROR_CODE) {
        throw error
      }
      logger.warn(`Ignoring duplicated event with etag ${eventTag}.`)
    }
  }
  return resources
}
export function rawEventsStreamToEnvelopes(
  config: BoosterConfig,
  context: Context,
  dedupEventStream: EventStream
): Array<EventEnvelope> {
  const logger = getLogger(config, 'events-adapter#rawEventsStreamToEnvelopes')
  logger.debug(`Mapping ${dedupEventStream.length} events`)
  const bindingData = context.bindingData
  return dedupEventStream.map((message, index) => {
    const rawParsed = JSON.parse(message as string)
    const instance = process.env.WEBSITE_INSTANCE_ID
    const partitionKeyArrayElement = bindingData.partitionKeyArray[index]
    const offset = bindingData.offsetArray[index]
    const sequence = bindingData.sequenceNumberArray[index]
    const time = bindingData.enqueuedTimeUtcArray[index]
    logger.debug(`CONSUMED_EVENT:${instance}#${partitionKeyArrayElement}=>(${index}#${offset}#${sequence}#${time})`)
    return rawParsed as EventEnvelope
  })
}
