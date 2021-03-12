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
} from './library/read-models-adapter'
import { rawGraphQLRequestToEnvelope } from './library/graphql-adapter'
import { DynamoDB, CognitoIdentityServiceProvider } from 'aws-sdk'
import { ProviderInfrastructure, ProviderLibrary, RocketDescriptor } from '@boostercloud/framework-types'
import { requestFailed, requestSucceeded } from './library/api-gateway-io'
import { searchReadModel } from './library/read-models-searcher-adapter'
import { searchEvents } from './library/events-searcher-adapter'
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

/* We load the infrastructure package dynamically here to avoid including it in the
 * dependences that are deployed in the lambda functions. The infrastructure
 * package is only used during the deploy.
 * Notice that this is done in a separate function to ease testing
 */
function loadInfrastructurePackage(
  packageName: string
): { Infrastructure: (rockets?: RocketDescriptor[]) => ProviderInfrastructure } {
  return require(packageName)
}

/**
 * `Provider` is a function that accepts a list of rocket names and returns an
 * object compatible with the `ProviderLibrary` defined in the `framework-types` package.
 * The rocket names are passed to the infrastructure package, which loads them dynamically
 * to extend the AWS functionality. Rockets are typically distributed in separate node packages.
 */
export const Provider = (rockets?: RocketDescriptor[]): ProviderLibrary => {
  return {
    // ProviderEventsLibrary
    events: {
      rawToEnvelopes: rawEventsToEnvelopes,
      forEntitySince: readEntityEventsSince.bind(null, dynamoDB),
      latestEntitySnapshot: readEntityLatestSnapshot.bind(null, dynamoDB),
      search: searchEvents.bind(null, dynamoDB),
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
    // ProviderInfrastructureGetter
    infrastructure: () => {
      const infrastructurePackageName = require('../package.json').name + '-infrastructure'
      try {
        return loadInfrastructurePackage(infrastructurePackageName).Infrastructure(rockets)
      } catch (e) {
        throw new Error(
          'The AWS infrastructure package could not be loaded. Please ensure that one of the following actions has been done:\n' +
            `  - It has been specified in your "devDependencies" section of your "package.json" file. You can do so by running 'npm install --save-dev ${infrastructurePackageName}'\n` +
            `  - Or it has been installed globally. You can do so by running 'npm install -g ${infrastructurePackageName}'`
        )
      }
    },
  }
}

export * from './constants'
