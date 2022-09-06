/* eslint-disable @typescript-eslint/no-explicit-any */
import { HasInfrastructure, ProviderLibrary, RocketDescriptor } from '@boostercloud/framework-types'
import { requestFailed, requestSucceeded } from './library/api-adapter'
import { rawGraphQLRequestToEnvelope } from './library/graphql-adapter'
import {
  rawEventsToEnvelopes,
  storeEvents,
  readEntityEventsSince,
  readEntityLatestSnapshot,
} from './library/events-adapter'
import { CosmosClient } from '@azure/cosmos'
import { environmentVarNames } from './constants'
import { deleteReadModel, fetchReadModel, storeReadModel } from './library/read-model-adapter'
import { searchReadModel } from './library/searcher-adapter'
import { rawScheduledInputToEnvelope } from './library/scheduled-adapter'
import { searchEvents, searchEntitiesIds, filteredSearchEvents } from './library/events-searcher-adapter'

let cosmosClient: CosmosClient
if (typeof process.env[environmentVarNames.cosmosDbConnectionString] === 'undefined') {
  cosmosClient = {} as any
} else {
  cosmosClient = new CosmosClient(process.env[environmentVarNames.cosmosDbConnectionString] as string)
}

/* We load the infrastructure package dynamically here to avoid including it in the
 * dependencies that are deployed in the lambda functions. The infrastructure
 * package is only used during the deploy.
 */
export function loadInfrastructurePackage(packageName: string): HasInfrastructure {
  return require(packageName)
}

export const Provider = (rockets?: RocketDescriptor[]): ProviderLibrary => ({
  // ProviderEventsLibrary
  events: {
    rawToEnvelopes: rawEventsToEnvelopes,
    store: storeEvents.bind(null, cosmosClient),
    forEntitySince: readEntityEventsSince.bind(null, cosmosClient),
    latestEntitySnapshot: readEntityLatestSnapshot.bind(null, cosmosClient),
    search: searchEvents.bind(null, cosmosClient),
    filteredSearch: filteredSearchEvents.bind(null, cosmosClient),
    searchEntitiesIDs: searchEntitiesIds.bind(null, cosmosClient),
  },
  // ProviderReadModelsLibrary
  readModels: {
    fetch: fetchReadModel.bind(null, cosmosClient),
    search: searchReadModel.bind(null, cosmosClient),
    subscribe: undefined as any,
    rawToEnvelopes: undefined as any,
    fetchSubscriptions: undefined as any,
    store: storeReadModel.bind(null, cosmosClient),
    delete: deleteReadModel.bind(null, cosmosClient),
    deleteSubscription: undefined as any,
    deleteAllSubscriptions: undefined as any,
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
    storeData: notImplemented as any,
    fetchData: notImplemented as any,
    deleteData: notImplemented as any,
    sendMessage: notImplemented as any,
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
    } catch (e) {
      throw new Error(
        `The Azure infrastructure package could not be loaded. The following error was thrown: ${e.message}. Please ensure that one of the following actions has been done:\n` +
          `  - It has been specified in your "devDependencies" section of your "package.json" file. You can do so by running 'npm install --save-dev ${infrastructurePackageName}'\n` +
          `  - Or it has been installed globally. You can do so by running 'npm install -g ${infrastructurePackageName}'`
      )
    }

    return infrastructure.Infrastructure(rockets)
  },
})

function notImplemented(): void {}

export * from './constants'
