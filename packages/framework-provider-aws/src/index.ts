/* eslint-disable @typescript-eslint/no-explicit-any */
import { rawCommandToEnvelope, submitCommands } from './library/commands-adapter'
import {
  rawEventsToEnvelopes,
  storeEvent,
  readEntityLatestSnapshot,
  readEntityEventsSince,
  publishEvents,
} from './library/events-adapter'
import {
  fetchReadModel,
  fetchAllReadModels,
  storeReadModel,
  rawReadModelRequestToEnvelope,
} from './library/read-model-adapter'
import { rawSignUpDataToUserEnvelope } from './library/auth-adapter'
import { Kinesis, DynamoDB, CognitoIdentityServiceProvider } from 'aws-sdk'
import { ProviderInfrastructure, ProviderLibrary } from '@boostercloud/framework-types'
import { requestFailed, requestSucceeded } from './library/api-gateway-io'

const eventsStream: Kinesis = new Kinesis()
const dynamoDB: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
const userPool = new CognitoIdentityServiceProvider()

export const Provider: ProviderLibrary = {
  // ProviderCommandsLibrary
  rawCommandToEnvelope: rawCommandToEnvelope.bind(null, userPool),
  submitCommands,

  // ProviderEventsLibrary
  rawEventsToEnvelopes,
  storeEvent: storeEvent.bind(null, dynamoDB),
  readEntityEventsSince: readEntityEventsSince.bind(null, dynamoDB),
  readEntityLatestSnapshot: readEntityLatestSnapshot.bind(null, dynamoDB),
  publishEvents: publishEvents.bind(null, eventsStream),

  // ProviderReadModelsLibrary
  rawReadModelRequestToEnvelope: rawReadModelRequestToEnvelope.bind(null, userPool),
  fetchReadModel: fetchReadModel.bind(null, dynamoDB),
  fetchAllReadModels: fetchAllReadModels.bind(null, dynamoDB),
  storeReadModel: storeReadModel.bind(null, dynamoDB),
  handleReadModelResult: requestSucceeded,
  handleReadModelError: requestFailed,

  // ProviderAuthLibrary
  rawSignUpDataToUserEnvelope,

  // ProviderAPIHandling
  requestSucceeded,
  requestFailed,

  // ProviderInfrastructureGetter
  getInfrastructure: () =>
    require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
}

export * from './constants'
