/* eslint-disable @typescript-eslint/no-explicit-any */
import { rawCommandToEnvelope, handleCommandResult } from './library/commands-adapter'
import {
  rawEventsToEnvelopes,
  storeEvent,
  readEntityLatestSnapshot,
  readEntityEventsSince,
} from './library/events-adapter'
import {
  fetchReadModel,
  fetchAllReadModels,
  storeReadModel,
  rawReadModelRequestToEnvelope,
} from './library/read-model-adapter'
import { rawGraphQLRequestToEnvelope } from './library/graphql-adapter'
import { rawSignUpDataToUserEnvelope } from './library/auth-adapter'
import { Kinesis, DynamoDB, CognitoIdentityServiceProvider } from 'aws-sdk'
import { ProviderInfrastructure, ProviderLibrary } from '@boostercloud/framework-types'
import { requestFailed, requestSucceeded } from './library/api-gateway-io'

const eventsStream: Kinesis = new Kinesis()
const dynamoDB: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
const userPool = new CognitoIdentityServiceProvider()

export const Provider: ProviderLibrary = {
  rawCommandToEnvelope: rawCommandToEnvelope.bind(null, userPool),
  handleCommandResult: handleCommandResult.bind(null, eventsStream),
  handleCommandError: requestFailed,

  rawEventsToEnvelopes,
  storeEvent: storeEvent.bind(null, dynamoDB),
  readEntityEventsSince: readEntityEventsSince.bind(null, dynamoDB),
  readEntityLatestSnapshot: readEntityLatestSnapshot.bind(null, dynamoDB),

  rawReadModelRequestToEnvelope: rawReadModelRequestToEnvelope.bind(null, userPool),
  fetchReadModel: fetchReadModel.bind(null, dynamoDB),
  fetchAllReadModels: fetchAllReadModels.bind(null, dynamoDB),
  storeReadModel: storeReadModel.bind(null, dynamoDB),
  handleReadModelResult: requestSucceeded,
  handleReadModelError: requestFailed,

  rawGraphQLRequestToEnvelope: rawGraphQLRequestToEnvelope.bind(null, userPool),
  handleGraphQLResult: requestSucceeded,
  handleGraphQLError: requestFailed,

  rawSignUpDataToUserEnvelope,

  getInfrastructure: () =>
    require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
}

export * from './constants'
