import { BoosterConfig, EventEnvelope } from '@boostercloud/framework-types'
import { CreateBatchOptions, EventHubProducerClient } from '@azure/event-hubs'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { partitionKeyForEvent } from './partition-keys'

export async function produceEventsStream(
  producer: EventHubProducerClient,
  entityName: string,
  entityID: string,
  eventEnvelopes: Array<EventEnvelope>,
  config: BoosterConfig
): Promise<void> {
  const logger = getLogger(config, 'events-stream-producer#produceEventsStream')
  logger.debug('Producing eventEnvelopes', eventEnvelopes)
  const batchOptions: CreateBatchOptions = {
    partitionKey: partitionKeyForEvent(entityName, entityID),
  }

  let batch = await producer.createBatch(batchOptions)
  let numEventsSent = 0
  let i = 0
  while (i < eventEnvelopes.length) {
    // messages can fail to be added to the batch if they exceed the maximum size configured for the EventHub.
    const eventEnvelope = eventEnvelopes[i]
    const isAdded = batch.tryAdd({ body: eventEnvelope })

    if (isAdded) {
      logger.info(`Added ${JSON.stringify(eventEnvelope)} to the batch`)
      ++i
      continue
    }

    if (batch.count === 0) {
      throw new Error(`Message was too large and can't be sent until it's made smaller. ${eventEnvelope}`)
    }

    // We reached the batch size limit
    logger.info(`Sending ${batch.count} messages`)
    await producer.sendBatch(batch)
    numEventsSent += batch.count

    batch = await producer.createBatch(batchOptions)
  }

  if (batch.count > 0) {
    logger.info(`Sending remaining ${batch.count} messages`)
    await producer.sendBatch(batch)
    numEventsSent += batch.count
  }

  logger.info(`Sent ${numEventsSent} events`)

  if (numEventsSent !== eventEnvelopes.length) {
    throw new Error(`Not all messages were sent (${numEventsSent}/${eventEnvelopes.length})`)
  }
}
