/* eslint-disable @typescript-eslint/no-explicit-any */
import { rawCommandToEnvelope } from './library/commands-adapter'
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
import { rawGraphQLRequestToEnvelope } from './library/graphql-adapter'
import { rawSignUpDataToUserEnvelope, authorizeRequest } from './library/auth-adapter'
import { Kinesis, DynamoDB, CognitoIdentityServiceProvider } from 'aws-sdk'
import { ProviderInfrastructure, ProviderLibrary } from '@boostercloud/framework-types'
import { requestFailed, requestSucceeded } from './library/api-gateway-io'
import { searchReadModel } from './library/searcher-adapter'
import {
  fetchSubscriptions,
  notifySubscription,
  rawReadModelEventsToEnvelopes,
  subscribeToReadModel,
} from './library/subscription-adapter'

const eventsStream: Kinesis = new Kinesis()
const dynamoDB: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
const userPool = new CognitoIdentityServiceProvider()

export const Provider: ProviderLibrary = {
  // ProviderCommandsLibrary
  rawCommandToEnvelope: rawCommandToEnvelope.bind(null, userPool),

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
  searchReadModel: searchReadModel.bind(null, dynamoDB),
  subscribeToReadModel: subscribeToReadModel.bind(null, dynamoDB),
  rawReadModelEventsToEnvelopes: rawReadModelEventsToEnvelopes,
  fetchSubscriptions: fetchSubscriptions.bind(null, dynamoDB),
  notifySubscription,
  storeReadModel: storeReadModel.bind(null, dynamoDB),
  handleReadModelResult: requestSucceeded,
  handleReadModelError: requestFailed,

  // ProviderGraphQLLibrary
  authorizeRequest: authorizeRequest.bind(null, userPool),
  rawGraphQLRequestToEnvelope: rawGraphQLRequestToEnvelope,
  handleGraphQLResult: requestSucceeded,
  handleGraphQLError: requestFailed,

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
