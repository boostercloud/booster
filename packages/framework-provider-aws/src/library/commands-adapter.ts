import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { BoosterConfig, CommandEnvelope, EventEnvelope, Logger } from '@boostercloud/framework-types'
import { Kinesis, CognitoIdentityServiceProvider } from 'aws-sdk'
import { PutRecordsRequestEntry } from 'aws-sdk/clients/kinesis'
import { fail, succeed } from './api-gateway-io'
import { partitionKeyForEvent } from './partition-keys'
import { UserEnvelopeBuilder } from './user-envelope-builder'

export async function rawCommandToEnvelope(
  userPool: CognitoIdentityServiceProvider,
  rawMessage: APIGatewayProxyEvent
): Promise<CommandEnvelope> {
  if (rawMessage.body) {
    const envelope = JSON.parse(rawMessage.body) as CommandEnvelope
    envelope.requestID = rawMessage.requestContext.requestId
    const accessToken = rawMessage.headers['Authorization']?.replace('Bearer ', '') // Remove the "Bearer" prefix
    if (accessToken) {
      const currentUserData = await userPool
        .getUser({
          AccessToken: accessToken,
        })
        .promise()
      envelope.currentUser = UserEnvelopeBuilder.fromAttributeList(currentUserData.UserAttributes)
    }
    return envelope
  } else {
    throw TypeError('The field "body" from the API Gateway Event arrived empty.')
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
  return succeed()
}

export async function handleCommandError(config: BoosterConfig, error: Error): Promise<APIGatewayProxyResult> {
  // TODO: differentiate between user errors and server errors
  // eslint-disable-next-line @typescript-eslint/no-magic-numbers
  return fail(500, error.name, error.message)
}

function toPutRecordsEntity(eventEnvelope: EventEnvelope): PutRecordsRequestEntry {
  return {
    // TODO: Here we should decide which partition key to use: Whether the ID of the event or the ID of the entity it
    // is related to. What if the event is related to more than one entity?
    PartitionKey: partitionKeyForEvent(eventEnvelope.entityTypeName, eventEnvelope.entityID),
    Data: Buffer.from(JSON.stringify(eventEnvelope)),
  }
}
