/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  rawEventsToEnvelopes,
  storeEvents,
  readEntityLatestSnapshot,
  readEntityEventsSince,
} from './library/events-adapter'
import { fetchReadModel, storeReadModel, rawReadModelEventsToEnvelopes } from './library/read-model-adapter'
import { rawGraphQLRequestToEnvelope } from './library/graphql-adapter'
import { DynamoDB, CognitoIdentityServiceProvider } from 'aws-sdk'
import { ProviderInfrastructure, ProviderLibrary } from '@boostercloud/framework-types'
import { requestFailed, requestSucceeded } from './library/api-gateway-io'
import { searchReadModel } from './library/searcher-adapter'
import {
  deleteAllSubscriptions,
  deleteSubscription,
  fetchSubscriptions,
  notifySubscription,
  subscribeToReadModel,
} from './library/subscription-adapter'
import { enrichRawMessage, rawSignUpDataToUserEnvelope } from './library/auth-adapter'

const dynamoDB: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
const userPool = new CognitoIdentityServiceProvider()

export const Provider: ProviderLibrary = {
  // ProviderEventsLibrary
  events: {
    rawToEnvelopes: rawEventsToEnvelopes,
    forEntitySince: readEntityEventsSince.bind(null, dynamoDB),
    latestEntitySnapshot: readEntityLatestSnapshot.bind(null, dynamoDB),
    store: storeEvents.bind(null, dynamoDB),
  },
  // ProviderReadModelsLibrary
  readModels: {
    rawToEnvelopes: rawReadModelEventsToEnvelopes,
    fetch: fetchReadModel.bind(null, dynamoDB),
    search: searchReadModel.bind(null, dynamoDB),
    store: storeReadModel.bind(null, dynamoDB),
    subscribe: subscribeToReadModel.bind(null, dynamoDB),
    fetchSubscriptions: fetchSubscriptions.bind(null, dynamoDB),
    notifySubscription,
    deleteSubscription: deleteSubscription.bind(null, dynamoDB),
    deleteAllSubscriptions: deleteAllSubscriptions.bind(null, dynamoDB),
  },
  // ProviderGraphQLLibrary
  graphQL: {
    rawToEnvelope: rawGraphQLRequestToEnvelope.bind(null, userPool),
    handleResult: requestSucceeded,
  },
  // ProviderAuthLibrary
  auth: {
    rawToEnvelope: rawSignUpDataToUserEnvelope,
    enrichRawMessage: enrichRawMessage,
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
