/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  rawEventsToEnvelopes,
  storeEvent,
  readEntityLatestSnapshot,
  readEntityEventsSince,
  publishEvents,
} from './library/events-adapter'
import { fetchReadModel, storeReadModel } from './library/read-model-adapter'
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
  // ProviderEventsLibrary
  events: {
    rawToEnvelopes: rawEventsToEnvelopes,
    store: storeEvent.bind(null, dynamoDB),
    forEntitySince: readEntityEventsSince.bind(null, dynamoDB),
    readEntityLatestSnapshot: readEntityLatestSnapshot.bind(null, dynamoDB),
    publish: publishEvents.bind(null, eventsStream),
  },
  // ProviderReadModelsLibrary
  readModels: {
    fetch: fetchReadModel.bind(null, dynamoDB),
    search: searchReadModel.bind(null, dynamoDB),
    subscribe: subscribeToReadModel.bind(null, dynamoDB),
    rawToEnvelopes: rawReadModelEventsToEnvelopes,
    fetchSubscriptions: fetchSubscriptions.bind(null, dynamoDB),
    notifySubscription,
    store: storeReadModel.bind(null, dynamoDB),
  },
  // ProviderGraphQLLibrary
  graphQL: {
    authorizeRequest: authorizeRequest.bind(null, userPool),
    rawToEnvelope: rawGraphQLRequestToEnvelope,
    handleResult: requestSucceeded,
  },
  // ProviderAuthLibrary
  auth: {
    rawToEnvelope: rawSignUpDataToUserEnvelope,
  },
  // ProviderAPIHandling
  api: {
    requestSucceeded,
    requestFailed,
  },
  // ProviderInfrastructureGetter
  infrastructure: () =>
    require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
}

export * from './constants'
