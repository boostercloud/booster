/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  rawEventsToEnvelopes,
  storeEvents,
  readEntityLatestSnapshot,
  readEntityEventsSince,
} from './library/events-adapter'
import {
  fetchReadModel,
  storeReadModel,
  rawReadModelEventsToEnvelopes,
  deleteReadModel,
} from './library/read-model-adapter'
import { rawGraphQLRequestToEnvelope } from './library/graphql-adapter'
import { DynamoDB, CognitoIdentityServiceProvider } from 'aws-sdk'
import { ProviderLibrary } from '@boostercloud/framework-types'
import { requestFailed, requestSucceeded } from './library/api-gateway-io'
import { searchReadModel } from './library/searcher-adapter'
import {
  deleteAllSubscriptions,
  deleteSubscription,
  fetchSubscriptions,
  subscribeToReadModel,
} from './library/subscription-adapter'
import { handleSignUpResult, rawSignUpDataToUserEnvelope, userEnvelopeFromAuthToken } from './library/auth-adapter'
import {
  deleteConnectionData,
  fetchConnectionData,
  sendMessageToConnection,
  storeConnectionData,
} from './library/connections-adapter'
import { rawScheduledInputToEnvelope } from './library/scheduled-adapter'

const dynamoDB: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
const userPool = new CognitoIdentityServiceProvider()

/**
 * `Provider` is an implementation of `ProviderLibrary` defined in the `framework-types` package.
 */
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
    delete: deleteReadModel.bind(null, dynamoDB),
    subscribe: subscribeToReadModel.bind(null, dynamoDB),
    fetchSubscriptions: fetchSubscriptions.bind(null, dynamoDB),
    deleteSubscription: deleteSubscription.bind(null, dynamoDB),
    deleteAllSubscriptions: deleteAllSubscriptions.bind(null, dynamoDB),
  },
  // ProviderGraphQLLibrary
  graphQL: {
    rawToEnvelope: rawGraphQLRequestToEnvelope,
    handleResult: requestSucceeded,
  },
  // ProviderAuthLibrary
  auth: {
    rawToEnvelope: rawSignUpDataToUserEnvelope,
    fromAuthToken: userEnvelopeFromAuthToken.bind(null, userPool),
    handleSignUpResult: handleSignUpResult,
  },
  // ProviderAPIHandling
  api: {
    requestSucceeded,
    requestFailed,
  },
  connections: {
    storeData: storeConnectionData.bind(null, dynamoDB),
    fetchData: fetchConnectionData.bind(null, dynamoDB),
    deleteData: deleteConnectionData.bind(null, dynamoDB),
    sendMessage: sendMessageToConnection,
  },
  // ScheduledCommandsLibrary
  scheduled: {
    rawToEnvelope: rawScheduledInputToEnvelope,
  },
  packageDescription: () => {
    const { name, version } = require('../package.json')
    return {
      name,
      version,
    }
  },
}

export * from './constants'
