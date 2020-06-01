import { Logger, BoosterConfig, EventEnvelope } from '@boostercloud/framework-types'
import { EventRegistry } from '..'

export async function storeEvents(
  eventsStream: EventRegistry,
  eventEnvelopes: Array<EventEnvelope>,
  _config: BoosterConfig,
  logger: Logger
): Promise<void> {
  logger.info('Publishing the following events:', eventEnvelopes)
  for (const event of eventEnvelopes) {
    await eventsStream.publish(event)
  }
}
