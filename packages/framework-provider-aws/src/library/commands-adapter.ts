import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import {
  BoosterConfig,
  CommandEnvelope,
  EventEnvelope,
  Logger,
  InvalidParameterError,
} from '@boostercloud/framework-types'
import { Kinesis, CognitoIdentityServiceProvider } from 'aws-sdk'
import { PutRecordsRequestEntry } from 'aws-sdk/clients/kinesis'
import { partitionKeyForEvent } from './partition-keys'
import { fetchUserFromRequest } from './user-envelopes'
import { requestSucceeded } from './api-gateway-io'

export async function rawCommandToEnvelope(
  userPool: CognitoIdentityServiceProvider,
  request: APIGatewayProxyEvent
): Promise<CommandEnvelope> {
  if (request.body) {
    const envelope = JSON.parse(request.body) as CommandEnvelope
    envelope.requestID = request.requestContext.requestId
    envelope.currentUser = await fetchUserFromRequest(request, userPool)
    return envelope
  } else {
    throw new InvalidParameterError('The field "body" from the API Gateway Event arrived empty.')
  }
}

async function publishEvents(
  logger: Logger,
  config: BoosterConfig,
  eventsStream: Kinesis,
  eventEnvelopes: Array<EventEnvelope>
): Promise<void> {
  logger.info('Publishing the following events:', eventEnvelopes)
  const publishResult = await eventsStream
    .putRecords({
      StreamName: config.resourceNames.eventsStream,
      Records: eventEnvelopes.map(toPutRecordsEntity),
    })
    .promise()
  logger.debug('Events published with result', publishResult.$response)
}

export async function handleCommandResult(
  eventsStream: Kinesis,
  config: BoosterConfig,
  eventEnvelopes: Array<EventEnvelope>,
  logger: Logger
): Promise<APIGatewayProxyResult> {
  await publishEvents(logger, config, eventsStream, eventEnvelopes)
  // We are handling commands directly from the APIGateway, so we must return the response structured as it expects
  return requestSucceeded()
}

function toPutRecordsEntity(eventEnvelope: EventEnvelope): PutRecordsRequestEntry {
  return {
    PartitionKey: partitionKeyForEvent(eventEnvelope.entityTypeName, eventEnvelope.entityID),
    Data: Buffer.from(JSON.stringify(eventEnvelope)),
  }
}
