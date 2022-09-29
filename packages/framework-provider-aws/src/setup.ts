/* eslint-disable @typescript-eslint/no-explicit-any */
import { HasInfrastructure, ProviderLibrary, RocketDescriptor } from '@boostercloud/framework-types'
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
      forEntitySince: readEntityEventsSince.bind(undefined, dynamoDB),
      latestEntitySnapshot: readEntityLatestSnapshot.bind(undefined, dynamoDB),
      search: searchEvents.bind(undefined, dynamoDB),
      store: storeEvents.bind(undefined, dynamoDB),
      searchEntitiesIDs: searchEntitiesIds.bind(undefined, dynamoDB),
    },
    // ProviderReadModelsLibrary
    readModels: {
      rawToEnvelopes: rawReadModelEventsToEnvelopes,
      fetch: fetchReadModel.bind(undefined, dynamoDB),
      search: searchReadModel.bind(undefined, dynamoDB),
      store: storeReadModel.bind(undefined, dynamoDB),
      delete: deleteReadModel.bind(undefined, dynamoDB),
      subscribe: subscribeToReadModel.bind(undefined, dynamoDB),
      fetchSubscriptions: fetchSubscriptions.bind(undefined, dynamoDB),
      deleteSubscription: deleteSubscription.bind(undefined, dynamoDB),
      deleteAllSubscriptions: deleteAllSubscriptions.bind(undefined, dynamoDB),
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
      storeData: storeConnectionData.bind(undefined, dynamoDB),
      fetchData: fetchConnectionData.bind(undefined, dynamoDB),
      deleteData: deleteConnectionData.bind(undefined, dynamoDB),
      sendMessage: sendMessageToConnection,
    },
    // ScheduledCommandsLibrary
    scheduled: {
      rawToEnvelope: rawScheduledInputToEnvelope,
    },
    // ProviderInfrastructureGetter
    infrastructure: () => {
      const infrastructurePackageName = require('../package.json').name + '-infrastructure'
      let infrastructure: HasInfrastructure | undefined
      try {
        infrastructure = loadInfrastructurePackage(infrastructurePackageName)
      } catch (error) {
        throw new Error(
          `The AWS infrastructure package could not be loaded. The following error was thrown: ${error.message}. Please ensure that one of the following actions has been done:\n` +
            `  - It has been specified in your "devDependencies" section of your "package.json" file. You can do so by running 'npm install --save-dev ${infrastructurePackageName}'\n` +
            `  - Or it has been installed globally. You can do so by running 'npm install -g ${infrastructurePackageName}'`
        )
      }

      return infrastructure.Infrastructure(rockets)
    },
  }
}

export * from './constants'
