import {
  BoosterConfig,
  CommandEnvelope,
  EventEnvelope,
  Logger,
  InvalidParameterError,
  UUID,
} from '@boostercloud/framework-types'
import { fetchUserFromRequest } from './user-envelopes'
import { UserRegistry, EventRegistry } from '../services'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export async function rawCommandToEnvelope(userRegistry: UserRegistry, request: any): Promise<CommandEnvelope> {
  if (request.body) {
    const envelope: CommandEnvelope = request.body
    envelope.requestID = UUID.generate().toString()
    envelope.currentUser = await fetchUserFromRequest(request, userRegistry)
    return envelope
  } else {
    throw new InvalidParameterError('The field "body" arrived empty.')
  }
}

async function publishEvents(
  logger: Logger,
  _config: BoosterConfig,
  eventsStream: EventRegistry,
  eventEnvelopes: Array<EventEnvelope>
): Promise<void> {
  logger.info('Publishing the following events:', eventEnvelopes)
  for (const event of eventEnvelopes) {
    await eventsStream.publish(event)
  }
}

export async function handleCommandResult(
  eventsStream: EventRegistry,
  config: BoosterConfig,
  eventEnvelopes: Array<EventEnvelope>,
  logger: Logger
): Promise<void> {
  await publishEvents(logger, config, eventsStream, eventEnvelopes)
}
