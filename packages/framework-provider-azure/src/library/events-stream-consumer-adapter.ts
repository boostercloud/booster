import { BoosterConfig, EventEnvelope, EventStream } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { AZURE_CONFLICT_ERROR_CODE } from '../constants'
import { CosmosClient } from '@azure/cosmos'
import { AzureEventHubFunctionInput, isEventHubFunctionInput } from '../types/azure-func-types'

interface DedupEventStream {
  primaryKey: string
  createdAt: string
  ttl: number
}

/**
 * V4 Programming Model: Input wrapper for Event Hub consumer functions.
 * Extends the base type with binding data for event metadata.
 */
export interface EventHubConsumerInput extends AzureEventHubFunctionInput {
  /** Binding data containing Event Hub metadata arrays */
  bindingData: {
    partitionKeyArray: string[]
    offsetArray: string[]
    sequenceNumberArray: number[]
    enqueuedTimeUtcArray: string[]
  }
}

const DEFAULT_DEDUP_TTL = 86400

/**
 * Deduplicates events from the Event Hub stream using Cosmos DB.
 * In v4 programming model, messages are passed in the input wrapper.
 * @param cosmosDb - The Cosmos DB client
 * @param config - The Booster configuration
 * @param rawInput - The raw input from the Azure Function, expected to be of type EventHubConsumerInput
 * @returns A promise that resolves to the deduplicated event stream
 */
export async function dedupEventStream(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  rawInput: unknown
): Promise<EventStream> {
  const logger = getLogger(config, 'dedup-events-stream#dedupEventsStream')

  let events: EventStream = []
  if (isEventHubFunctionInput(rawInput)) {
    events = (rawInput as AzureEventHubFunctionInput).messages as EventStream
  } else if (Array.isArray(rawInput)) {
    // Direct array for testing
    events = rawInput as EventStream
  }

  logger.debug(`Dedup ${events.length} events`)

  const resources: EventStream = []
  for (const event of events) {
    // In v4, Event Hub messages may already be parsed objects, not JSON strings
    const rawParsed = typeof event === 'string' ? JSON.parse(event) : event
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
      if ((error as { code?: number }).code !== AZURE_CONFLICT_ERROR_CODE) {
        throw error
      }
      logger.warn(`Ignoring duplicated event with etag ${eventTag.primaryKey}.`)
    }
  }
  return resources
}

/**
 * Converts raw Event Hub events to Booster EventEnvelope format.
 * In v4 programming model, binding data is passed via triggerMetadata on the context.
 * @param config - Booster configuration
 * @param rawInput - The raw input from the Azure Function
 * @param dedupEventStream - The deduplicated event stream
 * @returns An array of EventEnvelope objects
 */
export function rawEventsStreamToEnvelopes(
  config: BoosterConfig,
  rawInput: unknown,
  dedupEventStream: EventStream
): Array<EventEnvelope> {
  const logger = getLogger(config, 'events-adapter#rawEventsStreamToEnvelopes')
  logger.debug(`Mapping ${dedupEventStream.length} events`)

  // Extract binding data from the input
  let bindingData: EventHubConsumerInput['bindingData'] | undefined
  if (isEventHubConsumerInput(rawInput)) {
    bindingData = rawInput.bindingData
  }

  return dedupEventStream.map((message, index) => {
    // In v4, Event Hub messages may already be parsed objects, not JSON strings
    const rawParsed = typeof message === 'string' ? JSON.parse(message) : message
    const instance = process.env.WEBSITE_INSTANCE_ID

    if (bindingData) {
      const partitionKeyArrayElement = bindingData.partitionKeyArray[index]
      const offset = bindingData.offsetArray[index]
      const sequence = bindingData.sequenceNumberArray[index]
      const time = bindingData.enqueuedTimeUtcArray[index]
      logger.debug(`CONSUMED_EVENT:${instance}#${partitionKeyArrayElement}=>(${index}#${offset}#${sequence}#${time})`)
    }

    return rawParsed as EventEnvelope
  })
}

/**
 * Type guard to check if the input is an Event Hub consumer input with binding data
 * @param input - The input to check
 * @returns True if the input is an Event Hub consumer input, false otherwise
 */
function isEventHubConsumerInput(input: unknown): input is EventHubConsumerInput {
  return (
    isEventHubFunctionInput(input) &&
    typeof (input as EventHubConsumerInput).bindingData === 'object' &&
    Array.isArray((input as EventHubConsumerInput).bindingData?.partitionKeyArray)
  )
}
