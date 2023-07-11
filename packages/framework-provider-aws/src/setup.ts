/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  BoosterConfig,
  HasInfrastructure,
  HealthEnvelope,
  ProviderLibrary,
  RocketDescriptor,
} from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import { requestFailed, requestSucceeded } from './library/api-gateway-io'
import {
  deleteConnectionData,
  fetchConnectionData,
  sendMessageToConnection,
  storeConnectionData,
} from './library/connections-adapter'
import {
  rawEventsToEnvelopes,
  readEntityEventsSince,
  readEntityLatestSnapshot,
  storeEvents,
  storeSnapshot,
} from './library/events-adapter'
import { searchEntitiesIds, searchEvents } from './library/events-searcher-adapter'
import { rawGraphQLRequestToEnvelope } from './library/graphql-adapter'
import {
  deleteReadModel,
  fetchReadModel,
  rawReadModelEventsToEnvelopes,
  storeReadModel,
} from './library/read-models-adapter'
import { searchReadModel } from './library/read-models-searcher-adapter'
import { rawScheduledInputToEnvelope } from './library/scheduled-adapter'
import {
  deleteAllSubscriptions,
  deleteSubscription,
  fetchSubscriptions,
  subscribeToReadModel,
} from './library/subscription-adapter'
import { rawRocketInputToEnvelope } from './library/rocket-adapter'

const dynamoDB: DynamoDB.DocumentClient = new DynamoDB.DocumentClient({
  maxRetries: 10,
  httpOptions: {
    timeout: 2000,
  },
})

/* We load the infrastructure package dynamically here to avoid including it in the
 * dependencies that are deployed in the lambda functions. The infrastructure
 * package is only used during the deploy.
 * Notice that this is done in a separate function to ease testing
 */
export function loadInfrastructurePackage(packageName: string): HasInfrastructure {
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
      searchEntitiesIDs: searchEntitiesIds.bind(null, dynamoDB),
      store: storeEvents.bind(null, dynamoDB),
      storeSnapshot: storeSnapshot.bind(null, dynamoDB),
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
    rockets: {
      rawToEnvelopes: rawRocketInputToEnvelope,
    },
    sensor: {
      databaseEventsHealthDetails: async (config: BoosterConfig): Promise<unknown> => notImplementedResult(),
      databaseReadModelsHealthDetails: async (config: BoosterConfig): Promise<unknown> => notImplementedResult(),
      isDatabaseEventUp: async (config: BoosterConfig): Promise<boolean> => notImplementedResult(),
      areDatabaseReadModelsUp: async (config: BoosterConfig): Promise<boolean> => notImplementedResult(),
      databaseUrls: async (config: BoosterConfig): Promise<Array<string>> => notImplementedResult(),
      isGraphQLFunctionUp: async (config: BoosterConfig): Promise<boolean> => notImplementedResult(),
      graphQLFunctionUrl: async (config: BoosterConfig): Promise<string> => notImplementedResult(),
      rawRequestToHealthEnvelope: (rawRequest: unknown): HealthEnvelope => {
        throw new Error('Not implemented')
      },
    },
    // ProviderInfrastructureGetter
    infrastructure: () => {
      const infrastructurePackageName = require('../package.json').name + '-infrastructure'
      let infrastructure: HasInfrastructure | undefined
      try {
        infrastructure = loadInfrastructurePackage(infrastructurePackageName)
      } catch (e) {
        throw new Error(
          `The AWS infrastructure package could not be loaded. The following error was thrown: ${e.message}. Please ensure that one of the following actions has been done:\n` +
            `  - It has been specified in your "devDependencies" section of your "package.json" file. You can do so by running 'npm install --save-dev ${infrastructurePackageName}'\n` +
            `  - Or it has been installed globally. You can do so by running 'npm install -g ${infrastructurePackageName}'`
        )
      }

      return infrastructure.Infrastructure(rockets)
    },
  }
}

function notImplementedResult() {
  return Promise.reject('Not implemented')
}

export * from './constants'
