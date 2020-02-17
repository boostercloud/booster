/* eslint-disable @typescript-eslint/no-explicit-any */
import { rawCommandToEnvelope, handleCommandResult, handleCommandError } from './library/commands-adapter'
import {
  rawEventsToEnvelopes,
  storeEvent,
  readEntityLatestSnapshot,
  readEntityEventsSince,
} from './library/events-adapter'
import { processReadModelAPICall, fetchReadModel, storeReadModel } from './library/read-model-adapter'
import { rawSignUpDataToUserEnvelope } from './library/auth-adapter'
import { Kinesis, DynamoDB, CognitoIdentityServiceProvider } from 'aws-sdk'

const eventsStream: Kinesis = new Kinesis()
const dynamoDB: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
const userPool = new CognitoIdentityServiceProvider()

export const Library = {
  rawCommandToEnvelope: rawCommandToEnvelope.bind(null, userPool),
  handleCommandResult: handleCommandResult.bind(null, eventsStream),
  handleCommandError,

  rawEventsToEnvelopes,
  storeEvent: storeEvent.bind(null, dynamoDB),
  readEntityEventsSince: readEntityEventsSince.bind(null, dynamoDB),
  readEntityLatestSnapshot: readEntityLatestSnapshot.bind(null, dynamoDB),

  processReadModelAPICall: processReadModelAPICall.bind(null, dynamoDB),
  fetchReadModel: fetchReadModel.bind(null, dynamoDB),
  storeReadModel: storeReadModel.bind(null, dynamoDB),

  rawSignUpDataToUserEnvelope,
}
