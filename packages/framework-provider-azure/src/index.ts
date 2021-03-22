/* eslint-disable @typescript-eslint/no-explicit-any */
import { ProviderInfrastructure, ProviderLibrary } from '@boostercloud/framework-types'
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
import { fetchReadModel, storeReadModel } from './library/read-model-adapter'
import { searchReadModel } from './library/searcher-adapter'

let cosmosClient: CosmosClient
if (typeof process.env[environmentVarNames.cosmosDbConnectionString] === 'undefined') {
  cosmosClient = {} as any
} else {
  cosmosClient = new CosmosClient(process.env[environmentVarNames.cosmosDbConnectionString] as string)
}

export const Provider = (): ProviderLibrary => ({
  // ProviderEventsLibrary
  events: {
    rawToEnvelopes: rawEventsToEnvelopes,
    store: storeEvents.bind(null, cosmosClient),
    forEntitySince: readEntityEventsSince.bind(null, cosmosClient),
    latestEntitySnapshot: readEntityLatestSnapshot.bind(null, cosmosClient),
    search: undefined as any,
  },
  // ProviderReadModelsLibrary
  readModels: {
    fetch: fetchReadModel.bind(null, cosmosClient),
    search: searchReadModel.bind(null, cosmosClient),
    subscribe: undefined as any,
    rawToEnvelopes: undefined as any,
    fetchSubscriptions: undefined as any,
    store: storeReadModel.bind(null, cosmosClient),
    delete: undefined as any,
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
    rawToEnvelope: undefined as any,
  },
  // ProviderInfrastructureGetter
  infrastructure: () =>
    require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
})

function notImplemented(): void {}

export * from './constants'
